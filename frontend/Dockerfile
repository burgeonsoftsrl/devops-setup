# syntax=docker/dockerfile:1
# Build context: frontend/   e.g.  docker build -t devops-setup/frontend frontend
#
# Targets:
#   runtime (default) - production build served by nginx on :4200 (docker compose)
#   dev               - `ng serve` on :4200 with live reload (Tilt syncs src/ into it)

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

FROM deps AS dev
COPY . .
EXPOSE 4200
# --poll: file events from synced files are not always delivered inside containers
CMD ["npx", "ng", "serve", "--host", "0.0.0.0", "--port", "4200", "--poll", "1000"]

FROM deps AS build
# The browser calls the gateway directly, so this is the gateway URL as seen from the host.
ARG API_URL=http://localhost:8080
COPY . .
RUN API_URL="$API_URL" npm run build:pages

FROM nginx:1.29-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 4200
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:4200/healthz >/dev/null || exit 1
