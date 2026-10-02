# syntax=docker/dockerfile:1
# One Dockerfile for both Spring Boot apps:
#   docker build --build-arg APP=api backend/
#   docker build --build-arg APP=worker --build-arg PORT=8090 backend/

# ---- build: compile the chosen app (+ the kernel it depends on) ----
FROM eclipse-temurin:25-jdk AS build
ARG APP=api
WORKDIR /src
COPY . .
# The cache mount keeps ~/.m2 between local builds, so dependencies download once.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ${APP} -am package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true
# Split the fat jar into layers: dependencies change rarely, application code often. No
# --launcher: app.jar + lib/*.jar run on the plain classpath, which the AOT cache below needs.
RUN cp ${APP}/target/${APP}-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- runtime: JRE only, non-root, layers ordered from least to most frequently changed ----
# Alpine JRE (ADR-0007): ~30 % smaller and 41 → 0 CVEs vs the Ubuntu JRE, measured in
# patterns/devops/01 §7. BusyBox gives the healthcheck its wget; there is no bash.
FROM eclipse-temurin:25-jre-alpine AS runtime
ARG APP=api
ARG PORT=8080
LABEL org.opencontainers.image.source="https://github.com/b-nimai/Masternova-Spring" \
      org.opencontainers.image.title="masternova-${APP}"
# Base images lag behind Alpine's security fixes; pull them in at build time (as the web image does).
RUN apk upgrade --no-cache && addgroup -S app && adduser -S -G app -H app
# Rarely-changing files first: a layer that comes after a changed one is rebuilt and re-shipped
# too, so the healthcheck script sits above the application layer (patterns/devops/01 §3).
COPY --chmod=755 healthcheck.sh /usr/local/bin/healthcheck
WORKDIR /app
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
# Java 25 AOT cache (patterns/devops/02 §5): a training run loads and links the classes once, at
# build time, so every start maps them instead: ~45 % faster to ready, ~60 MB less memory. It
# exits at context refresh (no web server, runners or schedulers) and must not touch the
# database, hence Flyway, schema validation and JDBC metadata are off for this run only.
RUN java -XX:AOTCacheOutput=app.aot \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -jar app.jar
USER app
EXPOSE ${PORT}
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 CMD ["healthcheck"]
# Size the heap from the container's memory limit, and die fast on OOM so the orchestrator restarts us.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    HEALTH_PORT=${PORT}
# If the cache can't be used (e.g. a different JVM), the JVM silently starts without it.
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "app.jar"]
