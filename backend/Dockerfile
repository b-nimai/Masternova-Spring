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
# Split the fat jar into layers: dependencies change rarely, application code often.
RUN cp ${APP}/target/${APP}-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- runtime: JRE only, non-root, layers ordered from least to most frequently changed ----
FROM eclipse-temurin:25-jre AS runtime
ARG APP=api
ARG PORT=8080
LABEL org.opencontainers.image.source="https://github.com/b-nimai/Masternova-Spring" \
      org.opencontainers.image.title="masternova-${APP}"
RUN groupadd --system app && useradd --system --gid app --no-create-home app
# Rarely-changing files first: a layer that comes after a changed one is rebuilt and re-shipped
# too, so the healthcheck script sits above the application layer (patterns/devops/01 §3).
COPY --chmod=755 healthcheck.sh /usr/local/bin/healthcheck
WORKDIR /app
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER app
EXPOSE ${PORT}
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 CMD ["healthcheck"]
# Size the heap from the container's memory limit, and die fast on OOM so the orchestrator restarts us.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    HEALTH_PORT=${PORT}
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
