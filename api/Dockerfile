# syntax=docker/dockerfile:1

# Minimal runtime image that expects the API JAR to be built on the host
FROM eclipse-temurin:21-jre

# Create non-root user
RUN useradd -ms /bin/bash appuser
WORKDIR /app

# Copy built artifact from host build context (ensure `make api-build` ran first)
COPY api/target/*-SNAPSHOT.jar /app/app.jar

# Defaults
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=default
EXPOSE 8080

USER appuser

# Allow extra args via JAVA_OPTS
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
