# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests clean package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

# Create non-root user
RUN useradd -ms /bin/bash appuser
WORKDIR /app

# Copy built artifact
COPY --from=build /workspace/target/*-SNAPSHOT.jar /app/app.jar

# Defaults
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=default
EXPOSE 8080

USER appuser

# Allow extra args via JAVA_OPTS
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
