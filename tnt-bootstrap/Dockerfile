# ══════════════════════════════════════════════════════════════════════════════
# TiiBnTick Core — Dockerfile
# Author: MANFOUO Braun — ENSP Yaoundé 2026
#
# ⚠️  IMPORTANT: The base image MUST be Debian-based (NOT Alpine).
#     Google OR-Tools JNI native libraries require glibc, which is NOT
#     available on Alpine Linux (musl libc).
#     Use eclipse-temurin:21-jre (Debian Bookworm) or equivalent.
# ══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: Extract Spring Boot layers ────────────────────────────────────
FROM eclipse-temurin:21-jre AS builder

WORKDIR /application

# Copy the fat JAR produced by spring-boot-maven-plugin repackage
COPY target/tnt-bootstrap-0.0.1.jar application.jar

# Extract layered JAR for optimized Docker layer caching
RUN java -Djarmode=layertools -jar application.jar extract


# ── Stage 2: Production image ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre

LABEL maintainer="MANFOUO Braun <manfouo.braun@tiibntick.com>"
LABEL org.opencontainers.image.title="TiiBnTick Core"
LABEL org.opencontainers.image.description="TiiBnTick Core Bootstrap — Modular Monolith Spring Boot Application"
LABEL org.opencontainers.image.version="0.0.1"
LABEL org.opencontainers.image.authors="MANFOUO Braun"
LABEL org.opencontainers.image.source="https://gitlab.com/tiibntick-org/tiibntick-core"

# ── Security: run as non-root user ─────────────────────────────────────────
RUN groupadd --system tiibntick && \
    useradd --system --gid tiibntick --home /app --shell /bin/false tiibntick

WORKDIR /app

# ── Copy layered JAR components (optimizes cache) ──────────────────────────
COPY --from=builder /application/dependencies/ ./
COPY --from=builder /application/spring-boot-loader/ ./
COPY --from=builder /application/snapshot-dependencies/ ./
COPY --from=builder /application/application/ ./

# ── Ownership ──────────────────────────────────────────────────────────────
RUN chown -R tiibntick:tiibntick /app

USER tiibntick

# ── Application port ───────────────────────────────────────────────────────
EXPOSE 8080

# ── Health check (Kubernetes liveness probe equivalent) ────────────────────
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider \
        http://localhost:8080/actuator/health/liveness || exit 1

# ── JVM tuning for containerized environments ──────────────────────────────
# - UseContainerSupport: honor cgroup memory limits
# - MaxRAMPercentage: use 75% of container memory for heap
# - ZGC: low-latency GC suitable for logistics real-time workloads
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseZGC \
               -XX:+ZGenerational \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

# ── Spring Boot layered JAR entry point ────────────────────────────────────
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
