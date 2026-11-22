# devops-setup

A zero-cost training monorepo for practising event-driven microservices + DevOps.

| Component | Tech | Local | Production (free tier) |
|---|---|---|---|
| `api-gateway` | Spring Cloud Gateway (WebFlux) | :8080 | Render |
| `user-service` | Spring Boot + JPA | :8081 → Postgres | Render + **Neon** (schema `public`) |
| `order-service` | Spring Boot + Spring Data MongoDB, Kafka producer | :8082 → Mongo, Kafka | Render + **MongoDB Atlas M0** (db `orders`) |
| `payment-service` | Spring Boot + JPA, Kafka consumer/producer | :8083 → Postgres, Kafka | Render + **Neon** (schema `payments`) |
| `notification-service` | Spring Boot + Spring Data MongoDB, Kafka consumer | :8084 → Mongo, Kafka | Render + **Atlas** (db `notifications`) |
| Kafka | Apache Kafka 4 (KRaft) | :9092 | managed free tier (see [Kafka in production](#kafka-in-production)) |
| `frontend` | Angular | :4200 | **Cloudflare Pages** |

Spring Boot 3.5, Java 21, Maven multi-module. Every service image is tuned to run in **512 MB RAM**
(`-Xmx256m`, capped metaspace/code cache/direct memory, Serial GC — see each `Dockerfile`).

```
browser ─► frontend (Cloudflare Pages)
              │ HTTP (CORS)
              ▼
          api-gateway ─┬─ /api/users/**         ─► user-service ─────────► Postgres (public)
                       ├─ /api/orders/**        ─► order-service ────────► Mongo (orders)
                       ├─ /api/payments/**      ─► payment-service ──────► Postgres (payments)
                       └─ /api/notifications/** ─► notification-service ─► Mongo (notifications)

   order-service ──OrderCreated──► [orders.created] ──► payment-service ──PaymentProcessed──► [payments.processed]
                                         │                                                         │
                                         └──────────────► notification-service ◄───────────────────┘
```

## Event flow

1. `POST /api/orders` stores the order in Mongo and publishes **`OrderCreated`** to `orders.created`
   (key = orderId).
2. **payment-service** consumes it, computes `price × quantity`, stores a payment (APPROVED, or
   DECLINED above `PAYMENT_APPROVAL_LIMIT`, default 1000) and publishes **`PaymentProcessed`** to
   `payments.processed`. A unique `orderId` makes redeliveries idempotent.
3. **notification-service** consumes both topics, logs a "notification" and stores it in Mongo.

Event contracts (`OrderCreated`, `PaymentProcessed`), topic names and the Kafka security wiring
live in the shared **`common`** module. Messages are JSON (Spring Kafka `JsonSerializer`, wrapped in
`ErrorHandlingDeserializer` on the consumer side so a bad message can't crash-loop a consumer).
Each service declares both topics (2 partitions); `KafkaAdmin` creates them if missing
(disable with `APP_KAFKA_CREATE_TOPICS=false`).

## Repository layout

```
pom.xml                  parent POM (modules + Spring Cloud BOM)
common/                  event records, topic names, Kafka SASL/CA env wiring, topic auto-config
api-gateway/  user-service/  order-service/  payment-service/  notification-service/
frontend/                Angular app calling the gateway
docker-compose.yml       postgres, mongo, kafka (KRaft) + the 5 services
Tiltfile                 fast inner dev loop with live_update
scripts/smoke-test.sh    end-to-end test through the gateway (local, CI and prod)
.github/workflows/       ci.yml (PRs) and deploy.yml (main)
.env.example             production env vars reference
```

## Configuration

Everything is configured through environment variables with local defaults.

| Service | Variable | Local default |
|---|---|---|
| all | `PORT` | 8080 … 8084 |
| api-gateway | `USER_SERVICE_URL`, `ORDER_SERVICE_URL`, `PAYMENT_SERVICE_URL`, `NOTIFICATION_SERVICE_URL` | `http://localhost:8081` … `:8084` |
| api-gateway | `CORS_ALLOWED_ORIGINS` (comma-separated, patterns allowed) | `http://localhost:4200` |
| user-, payment-service | `DB_URL` | `jdbc:postgresql://localhost:5432/app` |
| user-, payment-service | `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
| user-, payment-service | `DB_POOL_SIZE`, `JPA_DDL_AUTO` | `5`, `update` |
| payment-service | `PAYMENTS_SCHEMA`, `PAYMENT_APPROVAL_LIMIT` | `payments`, `1000` |
| order-service | `MONGODB_URI` | `mongodb://localhost:27017/orders` |
| notification-service | `MONGODB_URI` | `mongodb://localhost:27017/notifications` |
| order-, payment-, notification-service | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| ″ | `KAFKA_USERNAME` / `KAFKA_PASSWORD` (set ⇒ `SASL_SSL`) | unset (PLAINTEXT) |
| ″ | `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-256` |
| ″ | `KAFKA_CA_CERT` (PEM; `\n` escapes OK) | unset (JVM default trust store) |
| frontend (build time) | `API_URL` | `http://localhost:8080` |

## Running locally

Prerequisites: JDK 21+, Node 20.19+ / 22+, Docker. Maven is not needed — use `./mvnw`.

### Option A — Docker Compose (everything in containers)

```bash
docker compose up --build --wait   # postgres, mongo, kafka + 5 services
scripts/smoke-test.sh              # end-to-end check through the gateway
cd frontend && npm ci && npm start # http://localhost:4200
```

### Option B — Tilt (recommended inner loop)

Install [Tilt](https://docs.tilt.dev/install.html), then:

```bash
tilt up        # UI: http://localhost:10350
```

Tilt loads `docker-compose.yml` and, for each Spring service:

1. `<service>-compile` runs `./mvnw package` on your machine (including `common` where used) and
   extracts the boot jar into `<service>/target/dev`;
2. builds the image from that output (`APP_SOURCE=local` stage of the Dockerfile — seconds, not minutes);
3. on every later change under `src/main` (or `common/src/main`), **live_update** syncs only the
   changed classes/jars into the running container and restarts it — no image rebuild.

Labels: **backend** (services + compile steps), **db** (postgres, mongo), **messaging** (kafka),
**frontend** (`npm ci` + `ng serve`).

### Option C — plain Maven

```bash
docker compose up -d --wait postgres mongo kafka
./mvnw -q install -DskipTests     # once, so services can resolve the common module
./mvnw -pl user-service spring-boot:run          # and likewise for the other services
```

### Inspecting Kafka locally

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders.created --from-beginning --property print.key=true
```

### Try it

```bash
curl -X POST localhost:8080/api/users -H 'Content-Type: application/json' \
     -d '{"name":"Ada","email":"ada@example.com"}'
curl -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -d '{"userId":1,"product":"Book","quantity":2,"price":9.99}'
curl "localhost:8080/api/payments?userId=1"
curl "localhost:8080/api/notifications?userId=1"
```

### Tests

```bash
./mvnw verify                      # unit + slice tests for all modules
scripts/smoke-test.sh [GATEWAY_URL] [TIMEOUT_S]   # full event flow against a running stack
```

## CI/CD

- **`ci.yml`** — on every pull request: `./mvnw verify`, Angular production build, and an
  **end-to-end job** that builds all images, starts the full compose stack (incl. Kafka) and runs
  `scripts/smoke-test.sh`.
- **`deploy.yml`** — on push to `main`: runs CI, deploys the frontend to Cloudflare Pages, and for
  each of the 5 services builds and pushes `ghcr.io/<owner>/devops-setup-<service>:{latest,<sha>}`
  and calls that service's Render deploy hook with `imgURL` set to the exact `<sha>` image.
  A missing hook secret only skips that deploy.

### Repository secrets and variables

| Name | Kind | Value |
|---|---|---|
| `RENDER_DEPLOY_HOOK_API_GATEWAY` … `_NOTIFICATION_SERVICE` (5) | secret | Render deploy hook URL per service |
| `CLOUDFLARE_API_TOKEN` | secret | token with *Account → Cloudflare Pages → Edit* |
| `CLOUDFLARE_ACCOUNT_ID` | secret | Cloudflare account ID |
| `API_URL` | variable | gateway URL baked into the frontend build |

`GITHUB_TOKEN` is used for GHCR — no extra secret needed.

## Deploying (all free tiers)

### 1. Data stores

- **Neon** (Postgres): one database for user-service (`public`) and payment-service (`payments`
  schema, created automatically). Use the *pooled* host:
  `jdbc:postgresql://<host>/<db>?sslmode=require`.
- **MongoDB Atlas** (M0, AWS Frankfurt `EU_CENTRAL_1`): one cluster, databases `orders` and
  `notifications`. Render free instances have no static egress IPs, so the IP access list must
  allow `0.0.0.0/0`; access is protected by the database user's credentials + TLS.
  ```bash
  atlas clusters create devops-setup --provider AWS --region EU_CENTRAL_1 --tier M0
  atlas accessLists create 0.0.0.0/0 --comment "Render free tier (no static IPs)"
  atlas dbusers create readWriteAnyDatabase --username devops-setup --password '<generated>'
  atlas clusters connectionStrings describe devops-setup
  ```

### 2. Kafka in production

Use a managed free tier with SASL_SSL; configure the services via the `KAFKA_*` variables above.
Create the topics `orders.created` and `payments.processed` (2 partitions) in the provider if it
doesn't allow clients to create them.

### 3. Publish images once

Push to `main` (or run the *Deploy* workflow manually) so the images exist in GHCR, then make each
`devops-setup-*` package **Public** (GitHub → Packages → Package settings → Change visibility).
There is no API for this.

### 4. Render services

For each service: **New → Web Service → Existing image**,
`ghcr.io/<owner>/devops-setup-<service>:latest`, instance type **Free**, region Frankfurt, health
check path `/actuator/health`, env vars per `.env.example`. Copy each service's
**Settings → Deploy Hook** URL into the matching GitHub secret (the Render API does not expose it).

> **Free-tier behaviour to know about**
> - Render free services **sleep after ~15 min without HTTP traffic** and wake on the next request
>   (30–60 s). A sleeping consumer doesn't consume — but Kafka keeps the events, so payment- and
>   notification-service catch up from their committed offsets as soon as they wake.
>   `scripts/smoke-test.sh` wakes every service first.
> - Render's free plan includes a monthly pool of instance hours shared by all free services;
>   don't add keep-alive pings for all 5 services or you'll exhaust it.

### 5. Cloudflare Pages (frontend)

The `devops-setup` Pages project is a **direct-upload** project, deployed by the `frontend` job in
`deploy.yml` on every push to `main` (via `wrangler pages deploy`). `npm run build:pages` writes
`API_URL` into the production environment before `ng build`; `public/_redirects` provides SPA
fallback routing. Manual deploy:

```bash
cd frontend
API_URL=https://api-gateway-xxxx.onrender.com npm run build:pages
CLOUDFLARE_API_TOKEN=... CLOUDFLARE_ACCOUNT_ID=... \
  npx wrangler pages deploy dist/frontend/browser --project-name devops-setup --branch main
```

### 6. Verify

```bash
scripts/smoke-test.sh https://api-gateway-xxxx.onrender.com 300
```
