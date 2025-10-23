# LigiTabl

Spring Boot service for football standings prediction.

## Requirements

- Java 21
- Maven 3.9+
- Docker (optional, for containers)
- Docker Compose (optional, for app + Postgres)

## Quick start (no DB)

Build and run without needing Postgres (skips JDBC):

```bash
make build
make run-no-db
# In another terminal
curl http://localhost:8080/api/status
```

## Full local run with Postgres

You can run the app with a local Postgres you manage, or let Docker Compose start one for you.

### Option A: Docker Compose (recommended)

```bash
make compose-up
# Check endpoint
curl http://localhost:8080/api/status
# Tear down when done
make compose-down
```

Notes:
- Compose wires the app to the `db` container (Postgres 16) with default creds defined in `application.yml`.
- Liquibase is disabled by default in Compose until changelogs are added.

### Option B: Local Postgres

```bash
# (macOS) Install and start Postgres
brew install postgresql@16
brew services start postgresql@16

# Create DB and user
psql postgres -c "CREATE USER ligitabl WITH PASSWORD 'ligitabl';" || true
psql postgres -c "CREATE DATABASE ligi OWNER ligitabl;" || true

# Run app
make build
make run
```

## Docker image

Build and run the image:

```bash
make docker-build
make docker-run
# Stop container
make docker-stop
```

Use `JAVA_OPTS` to pass extra JVM args to the container:

```bash
JAVA_OPTS="-Xms256m -Xmx512m" make docker-run
```

## jOOQ code generation

Requires providing DB connection details via environment variables:

```bash
export JOOQ_DB_URL="jdbc:postgresql://localhost:5432/ligi"
export JOOQ_DB_USER="ligitabl"
export JOOQ_DB_PASSWORD="ligitabl"
make codegen
```

## Endpoints

- `GET /api/status` — simple status check

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelog path is configured but not yet present. Keep it disabled until you add changelogs (or I can scaffold them for you).
