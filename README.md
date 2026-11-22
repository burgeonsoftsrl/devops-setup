# devops-setup

A zero-cost training monorepo for practising microservices + DevOps:

| Component | Tech | Local | Production (free tier) |
|---|---|---|---|
| `api-gateway` | Spring Cloud Gateway (WebFlux) | :8080 | Render (Docker image from GHCR) |
| `user-service` | Spring Boot + JPA | :8081 → Postgres | Render + **Neon** Postgres |
| `order-service` | Spring Boot + Spring Data Redis | :8082 → Redis | Render + **Upstash** Redis |
| `frontend` | Angular | :4200 | **Cloudflare Pages** |

Spring Boot 3.5, Java 21, Maven multi-module. Every container image is tuned to run in **512 MB RAM**
(`-Xmx256m`, capped metaspace/code cache/direct memory, Serial GC — see each `Dockerfile`).

```
browser ──► frontend (Cloudflare Pages)
              │  HTTP (CORS)
              ▼
          api-gateway ──/api/users/**──► user-service ──► Postgres / Neon
                      └─/api/orders/**─► order-service ─► Redis / Upstash
```

## Repository layout

```
pom.xml                  parent POM (modules + Spring Cloud BOM)
api-gateway/             routes /api/users/** and /api/orders/**, global CORS
user-service/            /api/users  (GET list, GET /{id}, POST)
order-service/           /api/orders (GET list [?userId=], GET /{id}, POST)
frontend/                Angular app calling the gateway
docker-compose.yml       postgres, redis, and the 3 services
Tiltfile                 fast inner dev loop with live_update
.github/workflows/       ci.yml (PRs) and deploy.yml (main)
.env.example             production env vars reference
```

## Configuration

Everything is configured through environment variables with local defaults, so the services run
out of the box against `docker compose` / localhost.

| Service | Variable | Local default |
|---|---|---|
| all | `PORT` | 8080 / 8081 / 8082 |
| api-gateway | `USER_SERVICE_URL` | `http://localhost:8081` |
| api-gateway | `ORDER_SERVICE_URL` | `http://localhost:8082` |
| api-gateway | `CORS_ALLOWED_ORIGINS` (comma-separated, patterns allowed) | `http://localhost:4200` |
| user-service | `DB_URL` | `jdbc:postgresql://localhost:5432/users` |
| user-service | `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
| user-service | `DB_POOL_SIZE`, `JPA_DDL_AUTO` | `5`, `update` |
| order-service | `REDIS_URL` (`rediss://` for TLS) | `redis://localhost:6379` |
| frontend (build time) | `API_URL` | `http://localhost:8080` |

## Running locally

Prerequisites: JDK 21+, Node 20.19+ / 22+, Docker. Maven is not needed — use `./mvnw`.

### Option A — Docker Compose (everything in containers)

```bash
docker compose up --build          # postgres, redis, user-service, order-service, api-gateway
cd frontend && npm ci && npm start # http://localhost:4200
```

### Option B — Tilt (recommended inner loop)

Install [Tilt](https://docs.tilt.dev/install.html), then:

```bash
tilt up        # UI: http://localhost:10350
```

Tilt loads `docker-compose.yml` and, for each Spring service:

1. `<service>-compile` runs `./mvnw package` on your machine and extracts the boot jar into
   `<service>/target/dev`;
2. builds the image from that output (`APP_SOURCE=local` stage of the Dockerfile — seconds, not minutes);
3. on every later change under `src/main`, **live_update** syncs only the changed classes/jars into
   the running container and restarts it — no image rebuild.

Resources are grouped by label: **backend** (services + compile steps), **db** (postgres, redis),
**frontend** (`npm ci` + `ng serve`).

### Option C — plain Maven

```bash
docker compose up -d postgres redis
./mvnw -pl user-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

### Try it

```bash
curl -X POST localhost:8080/api/users -H 'Content-Type: application/json' \
     -d '{"name":"Ada","email":"ada@example.com"}'
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -d '{"userId":1,"product":"Book","quantity":2,"price":9.99}'
curl localhost:8080/api/orders?userId=1
curl localhost:8080/actuator/health
```

### Tests

```bash
./mvnw verify                      # all modules (user-service tests use in-memory H2)
cd frontend && npm run build
```

## CI/CD

- **`ci.yml`** — on every pull request: `./mvnw verify`, Angular production build, and a Docker
  build of each service (no push).
- **`deploy.yml`** — on push to `main`: runs the CI jobs, then for each service builds and pushes
  `ghcr.io/<owner>/devops-setup-<service>:{latest,<sha>}` and calls that service's Render deploy
  hook with `imgURL` set to the exact `<sha>` image. A missing hook secret only skips that deploy.

### Repository secrets

| Secret | Value |
|---|---|
| `RENDER_DEPLOY_HOOK_API_GATEWAY` | Deploy hook URL of the Render `api-gateway` service |
| `RENDER_DEPLOY_HOOK_USER_SERVICE` | Deploy hook URL of the Render `user-service` service |
| `RENDER_DEPLOY_HOOK_ORDER_SERVICE` | Deploy hook URL of the Render `order-service` service |

`GITHUB_TOKEN` is used for GHCR — no extra secret needed.

## Deploying (all free tiers)

### 1. Databases

- **Neon**: create a project → copy the connection details. Build the JDBC URL from the *pooled*
  host: `jdbc:postgresql://<host>/<db>?sslmode=require`. The schema is created automatically
  (`JPA_DDL_AUTO=update`).
- **Upstash**: create a Redis database → copy the TLS URL: `rediss://default:<password>@<host>:6379`.

### 2. Publish images once

Push to `main` (or run the *Deploy* workflow manually) so the images exist in GHCR. Then in
GitHub → your profile → **Packages**, open each `devops-setup-*` package → *Package settings* →
change visibility to **Public** (lets Render pull without credentials; alternatively add a GHCR
registry credential in Render).

### 3. Render services

For each service: **New → Web Service → Existing image**, image
`ghcr.io/<owner>/devops-setup-<service>:latest`, instance type **Free** (512 MB), health check path
`/actuator/health`. Environment variables (see `.env.example`):

- `user-service`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `order-service`: `REDIS_URL`
- `api-gateway`: `USER_SERVICE_URL`, `ORDER_SERVICE_URL` (the two Render URLs, `https://…onrender.com`),
  `CORS_ALLOWED_ORIGINS` (your Pages URL, e.g. `https://devops-setup.pages.dev,https://*.devops-setup.pages.dev`)

Render sets `PORT`; the apps honour it. Then copy each service's **Settings → Deploy Hook** URL into
the matching GitHub secret above.

> Free Render services sleep after ~15 min idle; the first request after that takes a while
> (the gateway may time out while a downstream service wakes up — just retry).

### 4. Cloudflare Pages (frontend)

Workers & Pages → **Create → Pages → Connect to Git** → select this repo:

| Setting | Value |
|---|---|
| Framework preset | None (or Angular) |
| Root directory | `frontend` |
| Build command | `npm run build:pages` |
| Build output directory | `dist/frontend/browser` |
| Env variable `API_URL` | `https://api-gateway-xxxx.onrender.com` |
| Env variable `NODE_VERSION` | `22` |

`build:pages` writes `API_URL` into the production environment before `ng build`;
`public/_redirects` provides SPA fallback routing. Every push to `main` redeploys the site, and PRs
get preview URLs (covered by the `*.pages.dev` CORS pattern above).
