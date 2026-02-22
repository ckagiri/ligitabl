# syntax=docker/dockerfile:1

# ============================================
# Build Stage
# ============================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and pom files first (for better layer caching)
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY api/pom.xml api/
COPY model/pom.xml model/
COPY jooq-codegen/pom.xml jooq-codegen/
COPY seed/pom.xml seed/

# Install parent POM to local Maven repo (needed so jooq-codegen's artifact
# descriptor can be resolved — it references the parent as a SNAPSHOT POM)
RUN ./mvnw -q -DskipTests -N install -B

# Copy and install jooq-codegen source before dependency:go-offline.
# It is a plugin *dependency* of the model module's jooq-codegen-maven plugin.
# Maven resolves plugin deps from the local repo, not the reactor, so it must
# be installed here or dependency:go-offline will fail to resolve it.
COPY jooq-codegen/src jooq-codegen/src
RUN ./mvnw -q -DskipTests -pl jooq-codegen install -B

# Download remaining dependencies (cached layer — only re-runs when pom files change)
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY api/src api/src
COPY model/src model/src

# Copy pre-generated jOOQ sources (generated in the CI test job and passed via
# GitHub Actions artifact). A live DB is not available during docker build, so
# codegen cannot run here; these files substitute for it.
COPY model/target/generated-sources/jooq model/target/generated-sources/jooq

# Build without 'clean' (fresh Docker env has no stale artifacts) and without
# jOOQ codegen (generated sources are already present above).
RUN ./mvnw package -DskipTests -B -pl api -am -Djooq.codegen.skip=true

# ============================================
# Runtime Stage
# ============================================
FROM eclipse-temurin:21-jre

# Install curl for healthchecks
RUN apt-get update && \
  apt-get install -y --no-install-recommends curl && \
  rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/api/target/ligitabl-api-*.jar app.jar

RUN chown -R appuser:appuser /app
USER appuser

# APP_ARGS: override to run one-shot modes (e.g. import, seed-season)
ENV JAVA_OPTS="" \
  SPRING_PROFILES_ACTIVE=default \
  APP_ARGS=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $APP_ARGS"]
