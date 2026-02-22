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

# Download dependencies (cached layer — only re-runs when pom files change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY api/src api/src
COPY model/src model/src
COPY jooq-codegen/src jooq-codegen/src

# Build (skip tests — already run in CI test job)
RUN ./mvnw clean package -DskipTests -B -pl api -am

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
