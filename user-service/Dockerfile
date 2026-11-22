# syntax=docker/dockerfile:1
# Build context: repository root, e.g.  docker build -f user-service/Dockerfile .
#
# APP_SOURCE=build (default, CI/prod): compile inside Docker with Maven.
# APP_SOURCE=local (Tilt): reuse the jar Tilt already built and extracted into
#                          user-service/target/dev, so the image build takes seconds.
ARG APP_SOURCE=build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY common/src common/src
COPY user-service/src user-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl user-service -am package -DskipTests \
 && java -Djarmode=tools -jar user-service/target/user-service.jar extract --launcher --destination /extracted

FROM scratch AS local
COPY user-service/target/dev/ /extracted/

FROM ${APP_SOURCE} AS source

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && chown app:app /app
COPY --from=source --chown=app:app /extracted/ ./
USER app

# Sized for a 512MB container (e.g. Render free tier): heap + metaspace +
# code cache + direct buffers + thread stacks stay comfortably under the limit.
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENV PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
