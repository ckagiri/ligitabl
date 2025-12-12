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

## Formatting (Palantir Java Format, 100-col width)

This repo standardizes Java formatting with Spotless + Palantir Java Format (100 columns).

- Format code (api, model):

```bash
make format
```

- Check formatting without modifying files (fails if changes are needed):

```bash
make format-check
```

Details:

- Palantir Java Format (via Spotless) enforces 100 columns and deterministic wrapping
- Unused imports are removed automatically; imports are ordered: `java, javax, org, com`
- `.editorconfig` enforces 4 spaces and max line length 100 for general editor behavior
- VS Code: on-save Java formatting is disabled to avoid conflicts; use `make format`
- IntelliJ: install the "Palantir Java Format" plugin (or rely on `make format`) for on-save consistency

Note:

- Formatting targets intentionally exclude `jooq-codegen/` as it is a small helper module and considered complete.

### Fluent method chains

Palantir favors a consistent, readable break strategy:

- One call per line when wrapping is required
- 100-column width; short chains may remain on one line
- Deterministic reflow; avoid hand-alignment (let the formatter decide)

Tips:

- If a chain gets long, consider naming the builder or extracting sub-expressions.
- Keep chains free of side effects; compute values before chaining.

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
- To run only the API in the background (and auto-start DB), use: `make compose-up-app`.
- View app logs with: `make compose-logs-app`. Stop just the app: `make compose-stop-app`. Status: `make compose-ps`.
  - Faster start without rebuild: `make compose-up-app-fast` (uses existing image; won’t pick up new code).
  - Stop only the DB: `make compose-stop-db`.
  - Refresh without codegen: `make compose-refresh` (stops app+db, starts DB, rebuilds and starts the app). Use when you have app code changes but no DB/schema changes.
  - Refresh with codegen: `make compose-refresh-gen` (runs jOOQ codegen before rebuild).
  - Refresh with migrations: `make compose-refresh-db` (runs Liquibase migrations then jOOQ codegen before rebuild). Use this after changing changelogs.
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

Code is generated in the `model` module. Connection info is taken from DB\_\* in `.env`.

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

If you want a one-liner that brings up Postgres via Docker Compose, runs Liquibase migrations, and generates jOOQ
classes for local development, use:

```bash
make model-codegen-local
```

### One-shot DB bootstrap

To spin up Postgres, reset the DB, apply migrations, generate jOOQ, and seed sample data in one step:

```bash
make db-bootstrap
```

This runs: compose-up-db → reset-db → migrate → codegen → seed.

## Typical dev/test flow

The most common backend workflow while iterating on the API is:

```bash
# 1) Fast API tests (no DB/jOOQ) against latest model jar
make test-api-no-jooq

# 2) Full DB-backed integration tests (*IT via Testcontainers + Liquibase)
make test-api-it

# Or run both steps in one go:
make test-api-all
```

Notes:

- `test-api-no-jooq` installs the `model` module with the `no-jooq` profile (skipping jOOQ codegen and model tests),
  then runs API tests without needing a live database.
- `test-api-it` runs only `*IT` tests (e.g., `StatusControllerIT`, round/competition integration tests) with a real
  Postgres via Testcontainers and Liquibase migrations.
- `test-api-all` is a convenience wrapper that first runs `test-api-no-jooq` and then `test-api-it`.

## Migrations and resets

We use Liquibase in the `model` module.

- Master changelog: `model/src/main/resources/db/changelog/db.changelog-master.yaml`
  - Initial changeset (creates `public.t_team`): `20251023_1_initial.yaml`
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

The API currently exposes the following routes:

- GET /api/status — simple service health/status

Teams:

- GET /api/teams — list all teams
- GET /api/teams?id={uuid} — get a team by UUID (query parameter)
- GET /api/teams/{slug} — get a team by slug (path parameter)
- POST /api/teams — create a team
  - Body: JSON with fields name, shortName, slug, tla
  - Response: 201 Created with Location: /api/teams/{id} and the created team payload
- PUT /api/teams/{id} — update a team by UUID
  - Body: JSON with fields name, shortName, slug, tla
  - Response: 200 OK with the updated team payload
- DELETE /api/teams/{id} — delete a team by UUID
  - Response: 204 No Content

Notes:

- Getting by ID uses a query parameter (id) to avoid ambiguity with slug in the path.
- Team payload shape (request/response):
  - name: string (required)
  - shortName: string (required)
  - slug: string (required, lowercase letters/digits/hyphens)
  - tla: string (required, exactly 3 characters)

Examples (curl):

```bash
# List teams
curl -s http://localhost:8080/api/teams | jq .

# Get by ID (query param)
curl -s "http://localhost:8080/api/teams?id=22b2c3d4-aaaa-bbbb-cccc-1234567890ab" | jq .

# Get by slug
curl -s http://localhost:8080/api/teams/arsenal | jq .

# Create
curl -s -X POST http://localhost:8080/api/teams \
   -H 'Content-Type: application/json' \
   -d '{
      "name":"Arsenal Football Club",
      "shortName":"Arsenal",
      "slug":"arsenal",
      "tla":"ARS"
   }' | jq .

# Update
curl -s -X PUT http://localhost:8080/api/teams/22b2c3d4-aaaa-bbbb-cccc-1234567890ab \
   -H 'Content-Type: application/json' \
   -d '{
      "name":"Arsenal FC",
      "shortName":"Arsenal",
      "slug":"arsenal",
      "tla":"ARS"
   }' | jq .

# Delete
curl -i -X DELETE http://localhost:8080/api/teams/22b2c3d4-aaaa-bbbb-cccc-1234567890ab
```

Error responses follow a consistent shape and HTTP status code:

```json
{
  "message": "Team not found: arsenal",
  "error": "Not Found",
  "status": 404,
  "path": "uri=/api/teams/arsenal",
  "timestamp": "2025-11-03T12:34:56.789"
}
```

## DSLContext: auto-config, codegen, and troubleshooting

- You do NOT need to define a `@Bean` for `DSLContext`. Spring Boot autoconfigures it via `spring-boot-starter-jooq` when a `DataSource` is present. In this codebase, `RepositoryConfig.teamDao(DSLContext)` correctly relies on the auto-provided bean.
- jOOQ code generation is a build-time task in the `model` module and is unrelated to runtime injection of `DSLContext`. Codegen runs during `generate-sources` (e.g., `make codegen` / `make model-compile`) based on your DB schema; the generated classes are then consumed at runtime but don’t affect auto-wiring.
- If `spring.datasource.*` is misconfigured or the JDBC driver is missing, `DSLContext` won’t be created and the app will fail at startup.

Quick checklist if startup fails or `curl` can’t connect:

1. Ensure the app is actually running

   - Use:
     - `make run-app` to start Postgres (Compose) and run the JAR, or
     - `make run` if your local Postgres is already up.
   - Optional: run everything in one go: `make bootstrap-run` (starts DB, reset+migrate+codegen+seed, then runs the app).
   - Healthcheck: `curl http://localhost:${PORT:-8080}/actuator/health` should return `{"status":"UP"}` when the app is ready.

2. Verify datasource settings

   - The app reads `SPRING_DATASOURCE_URL` directly, or builds it from `DB_*` (host defaults to `localhost`, port defaults to `55432`).
   - Confirm env variables (or `.env` loaded by Makefile/Compose): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
   - Example URL: `jdbc:postgresql://localhost:55432/ligitabl`.

3. Confirm the Postgres driver is on the classpath

   - The API includes `org.postgresql:postgresql`; building with `mvn -pl api -am package` produces a runnable JAR with the driver.

4. Check logs for autoconfiguration hints

   - On failure you’ll typically see messages like “Failed to configure a DataSource” or “No qualifying bean of type ‘org.jooq.DSLContext’”. These indicate the datasource couldn’t be created (bad URL/creds/DB down) or the driver is missing.

5. Port already in use
   - If the server can’t start on `8080`, set a different port: `PORT=8081 make run-app` and curl `http://localhost:8081/...`.

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelogs are present. Runtime Liquibase is disabled by default; enable with the `liquibase` Spring profile or use `make migrate`.
- A `.env.example` file is provided. Create your own `.env` (not committed) to customize PORT, DB credentials, and Spring datasource.
