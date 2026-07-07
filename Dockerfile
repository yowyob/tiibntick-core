# ══════════════════════════════════════════════════════════════════════════════
# TiiBnTick Core — Dockerfile auto-suffisant (build du reactor 31 modules + run)
# Base Debian OBLIGATOIRE (OR-Tools JNI = glibc, pas musl/Alpine).
# ══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: build du reactor complet, jar bootstrap repackagé ──────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl tnt-bootstrap -am -DskipTests clean package \
    && cp tnt-bootstrap/target/tnt-bootstrap-*.jar /workspace/app.jar

# ── Stage 2: image runtime (Debian JRE pour OR-Tools) ───────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system tiibntick \
    && useradd --system --gid tiibntick --home /app --shell /bin/false tiibntick
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R tiibntick:tiibntick /app
USER tiibntick
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
