#!/usr/bin/env bash
# Builds the Spring Boot jars on the host (using the local/cached ~/.m2) and extracts each into
# <service>/target/dev, the input of the Dockerfiles' APP_SOURCE=local stage. Docker builds then
# need no Maven or network access - used by CI/deploy (avoids Maven Central rate limits) and
# handy locally:  scripts/build-jars.sh && APP_SOURCE=local docker compose up --build
#
# Usage: scripts/build-jars.sh [service ...]   (default: all five)
set -euo pipefail
cd "$(dirname "$0")/.."

ALL=(api-gateway user-service order-service payment-service notification-service)
services=("${@:-${ALL[@]}}")
modules=$(IFS=,; echo "${services[*]}")

./mvnw -B -q -pl "$modules" -am package -DskipTests

for s in "${services[@]}"; do
  rm -rf "$s/target/dev"
  java -Djarmode=tools -jar "$s/target/$s.jar" extract --launcher --destination "$s/target/dev" >/dev/null
  echo "extracted $s -> $s/target/dev"
done
