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
- Liquibase is disabled by default; enable it at runtime with the Spring profile `liquibase`:
	- Temporary: `SPRING_PROFILES_ACTIVE=liquibase docker compose up -d app`
	- Or add `SPRING_PROFILES_ACTIVE: liquibase` under `app.environment` in `docker-compose.yml`.
- Compose reads variables from a local `.env` file automatically and also passes them into the containers via `env_file`.
 - Default local DB port mapping is `55432` (host) -> `5432` (container). The app falls back to port `55432` when DB_PORT isn't set.

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

Code is generated in the `model` module. Connection info is taken from DB_* in `.env`.

```bash
cp .env.example .env   # optional: adjust DB_* if needed
make codegen           # builds model and generates to model/target/generated-sources/jooq
```

Tip: If you’re starting from scratch, you can reset, migrate, and seed first:

```bash
make reset-db    # drop and recreate the database inside the dockerized Postgres
make migrate     # apply Liquibase migrations (initial: 20251023-initial.yaml)
make seed        # insert a few example teams (idempotent)
make codegen     # regenerate jOOQ classes
```

### One-shot DB bootstrap

To spin up Postgres, reset the DB, apply migrations, generate jOOQ, and seed sample data in one step:

```bash
make db-bootstrap
```

This runs: compose-up-db → reset-db → migrate → codegen → seed.

## Migrations and resets

We use Liquibase in the `model` module.

- Master changelog: `model/src/main/resources/db/changelog/db.changelog-master.yaml`
- Initial changeset (creates `public.t_team`): `20251023-initial.yaml`
	- Naming convention: `YYYYMMDD-description.yaml`
	- Precondition: will be marked as ran if the table already exists.

Common tasks:

```bash
make compose-up-db   # ensure the DB container is up (host port defaults to 55432)
make reset-db        # drop and recreate the database
make migrate         # apply Liquibase changesets
make codegen         # regenerate jOOQ sources against the current schema
```

## Seeding the database

You can seed sample teams using either Docker (recommended when using Compose) or your local `psql` client.

### Option A: Docker (Compose DB)

Prereqs:
- Docker Compose DB is running (`ligitabl-db`). If not, start it with:

```bash
docker compose up -d db
```

Steps:

```bash
make prep-team  # drops/creates public.t_team
make seed       # inserts/updates sample teams
```

### Option B: Local psql client

Prereqs:
- `psql` installed locally.
- `.env` has `DB_*` values (for Compose-exposed DB, set `DB_PORT=55432`).

Steps:

```bash
make prep-team-local
make seed-local
```

Troubleshooting:
- If the Docker targets complain the DB isn’t running, do `docker compose up -d db`.
- For local targets, ensure `psql` is installed and `DB_PASSWORD` is set in your `.env`.
- Connection/auth errors: verify `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` in `.env`.

## Liquibase at runtime

Liquibase is disabled by default. To enable migrations during app startup, use the Spring profile:

```bash
# Local run
SPRING_PROFILES_ACTIVE=liquibase make run

# Docker Compose (temporary override)
SPRING_PROFILES_ACTIVE=liquibase docker compose up -d app
```

Alternatively, add this to `api/src/main/resources/application-liquibase.yml` is already present and sets `spring.liquibase.enabled=true`.

## Endpoints

- `GET /api/status` — simple status check

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelogs are present. Runtime Liquibase is disabled by default; enable with the `liquibase` Spring profile or use `make migrate`.
- A `.env.example` file is provided. Create your own `.env` (not committed) to customize PORT, DB credentials, and Spring datasource.
