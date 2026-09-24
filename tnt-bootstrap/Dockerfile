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
# Fixed absolute caps, not MaxRAMPercentage: MaxRAMPercentage sizes the heap
# against whatever the JVM detects as "container memory" — on a host/VM with
# no cgroup memory limit actually enforced (nothing in this repo's prod path
# sets one), UseContainerSupport falls back to the *physical host RAM*, so
# 75% of e.g. a 16 GB prod host became a multi-GB heap. Explicit -Xmx makes
# the ceiling deterministic no matter what the orchestrator does or doesn't
# enforce. Total budget below (~750 MB across heap+metaspace+direct+code
# cache) targets an 800 MB RSS ceiling once native libs (OR-Tools JNI,
# Netty epoll) and thread stacks are added — this is a first-pass estimate;
# re-tune Xmx/MaxMetaspaceSize from real RSS numbers (see docs/ops or ask).
# - ActiveProcessorCount=2: caps every core-count-derived default (Netty
#   event-loop threads, Reactor Schedulers.parallel(), G1 GC worker threads,
#   ForkJoinPool.commonPool) regardless of how many cores the prod host has —
#   this was very likely the main driver of the 125% baseline CPU, since an
#   idle JVM on a many-core box otherwise spins one worker thread per core
#   across several of those pools simultaneously. Raise it (with evidence
#   from real load, not guesswork) only if throughput under real traffic
#   needs it — OR-Tools' own solver worker count is configured separately in
#   code and is not affected by this flag.
# - G1GC replaces ZGC: ZGC is built for large heaps chasing sub-millisecond
#   pauses and keeps concurrent marking/relocation threads running
#   continuously; for a small heap that tolerates ordinary pause times, G1
#   is far cheaper on both idle memory and idle CPU.
# - Reactor/Netty system properties cap thread-pool fan-out the same way
#   ActiveProcessorCount does, explicitly, so it doesn't silently drift back
#   up if a future JDK changes its core-detection defaults.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:ActiveProcessorCount=2 \
               -Xms128m -Xmx320m \
               -XX:MaxMetaspaceSize=192m \
               -XX:MaxDirectMemorySize=96m \
               -XX:ReservedCodeCacheSize=48m \
               -Xss256k \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/tnt-core-oom.hprof \
               -XX:+ExitOnOutOfMemoryError \
               -Dreactor.netty.ioWorkerCount=4 \
               -Dreactor.netty.pool.maxConnections=50 \
               -Dreactor.schedulers.defaultPoolSize=2 \
               -Dreactor.schedulers.defaultBoundedElasticSize=20 \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

# ── Spring Boot layered JAR entry point ────────────────────────────────────
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
