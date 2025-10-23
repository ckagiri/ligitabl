# LigiTabl Monorepo

Repository layout for backend and frontends:

- `api/` — Spring Boot (Java 21) REST API
- `admin/` — React admin UI (placeholder for now)
- `app/` — React game app (placeholder for now)

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
cp .env.example .env   # optional: then tweak values
make compose-up
# Check endpoint
curl http://localhost:8080/api/status
# Tear down when done
make compose-down
```

Notes:
- Compose builds the API from `./api` and wires it to the `db` container (Postgres 16).
- Liquibase is disabled by default in Compose until changelogs are added.
- Compose reads variables from a local `.env` file automatically and also passes them into the containers via `env_file`.

### Option B: Local Postgres

```bash
# (macOS) Install and start Postgres
brew install postgresql@16
brew services start postgresql@16

# Create DB and user
psql postgres -c "CREATE USER ligitabl WITH PASSWORD 'ligitabl';" || true
psql postgres -c "CREATE DATABASE ligitabl OWNER ligitabl;" || true

# Run app
make build
make run
```

## Docker image

Build and run the image:

```bash
make docker-build  # builds using ./api/Dockerfile
cp .env.example .env   # optional
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
cp .env.example .env   # optional: then tweak values
export $(grep -v '^#' .env | xargs)  # load .env into the shell
make codegen  # runs against the Maven project in ./api
```

## Endpoints

- `GET /api/status` — simple status check

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelog path is configured but not yet present. Keep it disabled until you add changelogs (or I can scaffold them for you).
- A `.env.example` file is provided. Create your own `.env` (not committed) to customize PORT, DB credentials, and Spring datasource.
