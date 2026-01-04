# Backend Development Guide

This guide covers formatting, running the backend with and without Postgres, Docker/Compose usage, jOOQ code generation, migrations, seeding, and troubleshooting.

## Repo docs

This repo has developer-facing documentation under `docs/` (this file, endpoints, debugging) plus additional internal runbooks/specs elsewhere in the repository.

When changing behavior (endpoints, validation, persistence), update the relevant internal specs/runbooks and the public docs.

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

## Running the API without a DB

The default developer workflow assumes a database (local Docker Compose or Testcontainers in tests).

## Full local run with Postgres

You can run the app with a local Postgres you manage, or let Docker Compose start one for you.

### Option A: Docker Compose (recommended)

```bash
# Verify environment files
make env-check

# Start DB + run API using the default safe environment (ENV=test)
make run-api
# Check endpoint
curl http://localhost:8080/api/status
# Tear down when done
make compose-down
```

Notes:

- Compose builds the API from `./api` and wires it to the `db` container (Postgres 16).
- Compose uses the selected env file from the Makefile (default `ENV=test` → `.env.test`).
- Default dev DB host port is `55432`; the test/smoke environment uses `55433` (see `.env.test`).

## Environment selection and safety checks

This repo uses a **safety-first Makefile** which enforces explicit environments and avoids silent fallbacks.

### Environments

- `ENV=test` (default): safe for experiments and destructive operations
- `ENV=dev`: daily development DB (destructive operations require confirmation)
- `ENV=prod`: production (blocked unless you set `PROD_CONFIRMED=yes`)

### Environment files (no fallbacks)

The Makefile requires an env file for the selected environment:

- `ENV=test` → `.env.test` (committed)
- `ENV=dev` → `.env.dev` (committed)
- `ENV=prod` → `.env.prod` (never commit; gitignored)

Optional local overrides:

- `.env.test.local`, `.env.dev.local`, `.env.prod.local` (gitignored)

If an env file is missing, Make fails loudly and tells you what template to copy:

```bash
cp env.test.template .env.test
cp env.dev.template .env.dev
cp env.prod.template .env.prod
```

### Discover your current config

```bash
make env-info           # ENV=test
make env-info ENV=dev
make env-info ENV=prod
```

### Production gating

Any `ENV=prod` Make target requires `PROD_CONFIRMED=yes`:

```bash
make migrate ENV=prod PROD_CONFIRMED=yes
```

For `drop-db` in production you will also be prompted to type the DB name.

### Database name validation

- In `ENV=test`, DB names should include `test` (warns otherwise)
- In `ENV=dev`, DB names should include `dev` (warns otherwise)
- In non-prod envs, obvious production names are blocked (e.g., `ligitabl_prod`)

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
make docker-run
# Stop container
make docker-stop
```

Use `JAVA_OPTS` to pass extra JVM args to the container:

```bash
JAVA_OPTS="-Xms256m -Xmx512m" make docker-run
```

## jOOQ code generation

Code is generated in the `model` module. Connection info is taken from DB\_\* in the selected env file (`.env.test`, `.env.dev`, or `.env.prod`).

```bash
make codegen           # builds model and generates to model/target/generated-sources/jooq
```

Tip: If you’re starting from scratch, you can use the one-liner:

```bash
make dev-reset   # reset DB, run migrations, generate jOOQ, seed reference data
```

If you want a one-liner that brings up Postgres via Docker Compose, runs Liquibase migrations, and generates jOOQ
classes for local development, use:

```bash
make model-codegen-local
```

### One-shot DB reset + seed

To spin up Postgres, reset the DB, apply migrations, generate jOOQ, and seed reference data in one step:

```bash
make dev-reset
```

This runs: compose-up-db → reset-db → migrate → codegen → db-seed (reference data).

## Typical dev/test flow

The most common backend workflows while iterating are:

```bash
# 1) Fast model + core API tests (no *ITs)
make test-dev

# Equivalent explicit form
make test-model-fast
make test-api-core
```

API-specific flows:

```bash
# Fast API tests (no DB/jOOQ) against latest model jar
make test-api-no-jooq

# Full DB-backed API integration tests (*IT via Testcontainers + Liquibase)
make test-api-it

# Or run both steps in one go:
make test-api-all
```

Notes:

- `test-dev` runs `test-model-fast` (model tests assuming jOOQ codegen already ran) followed by `test-api-core`
  (API tests with `-DskipITs`).
- `test-api-no-jooq` installs the `model` module with the `no-jooq` profile (skipping jOOQ codegen and model tests),
  then runs API tests without needing a live database.
- `test-api-it` runs only `*IT` tests (e.g., `StatusControllerIT`, round/competition integration tests) with a real
  Postgres via Testcontainers and Liquibase migrations.
- `test-api-all` is a convenience wrapper that first runs `test-api-no-jooq` and then `test-api-it`.

## Running tests (quick reference)

Most day-to-day work uses Make targets (they encode the repo’s intended flags and profiles).

### API

- Core/unit API tests (skips `*IT`):

```bash
make test-api-core

# Equivalent:
mvn -q -pl api -am -DskipITs test
```

- DB-backed API integration tests (`*IT` via Testcontainers + Liquibase):

```bash
make test-api-it
```

- Full API suite (everything):

```bash
make test-api-all

# Equivalent:
mvn -pl api -am test
```

- Fast API tests without jOOQ/DB (installs model jar using `no-jooq` first):

```bash
make test-api-no-jooq
```

### Model + seed modules

- Model tests:

```bash
make test-model
```

- Seed module tests (hermetic DB-backed via Testcontainers):

```bash
mvn -q -pl seed -am test
```

### Full repo

```bash
make test-all
```

## Migrations and resets

We use Liquibase in the `model` module.

- Master changelog: `model/src/main/resources/db/changelog/db.changelog-master.yaml`
  - Initial changeset (creates `public.t_team`): `20251023_1_initial.yaml`
  - Naming convention: `YYYYMMDD-description.yaml`
  - Precondition: will be marked as ran if the table already exists.

Common tasks:

````bash
make compose-up-db   # ensure the DB container is up (dev host port defaults to 55432; tests use 55433 via .env.test)
make reset-db        # drop and recreate the database
make migrate         # apply Liquibase changesets
make codegen         # regenerate jOOQ sources against the current schema

## Importing matches (Football-Data)

The repo includes a headless match importer workflow you can run after seeding.

Make targets:

```bash
make import-competition COMP=PL
make import-pl
````

Notes:

- These Make targets use the selected env file (`.env.test`, `.env.dev`, or `.env.prod`) plus the optional local override for that env.
- Set your token in `.env.dev.local` (preferred) as `API_FOOTBALL_DATA_KEY=...` (or `FOOTBALL_DATA_API_TOKEN=...`).
- Importer targets start DB (compose), seed reference data, build the API jar, then run the workflow in headless mode.

````

## Seeding the database

Database seeding is handled via the dedicated `seed` module, using YAML configuration and the same Spring Boot stack as the API.

Note:

- The `seed` module integration tests run against an ephemeral Postgres via Testcontainers (with Liquibase enabled), so they do not depend on your local Docker Compose database state.

Common flows:

```bash
make db-seed      # seed reference data (competitions, seasons, rounds, teams)
make db-seed-demo # seed demo league data (demo competition, season, rounds, teams, matches)
make db-seed-all  # run both of the above

### Seeding auth users (for API smoke tests)

The repo includes a curl-based smoke script at [scripts/TestAuth.sh](scripts/TestAuth.sh) which logs in and hits role-protected endpoints.
Those users can be seeded into your dev database using:

- `make compose-up-db`
- `make migrate` (ensures the `t_user` / `t_user_role` tables exist)
- `make db-seed-users`
````

For a typical local dev reset + reference seeding in one go, use:

```bash
make dev-reset       # reset DB, migrate, codegen, seed reference data
make dev-reset-all   # same as above, plus demo data
```

### Seeding smoke script

For a quick end-to-end verification of "reset DB → migrate → seed reference → assert contest + standings", run:

```bash
./scripts/TestSeeding.sh
```

Notes:

- Destructive scripts require `.env.test` (and optional `.env.test.local`) and do not fall back to `.env`.
- Matchday/round positions are 1-based; the script asserts a round-1 standings row.

## Liquibase at runtime

Liquibase is disabled by default. To enable migrations during app startup, use the Spring profile:

```bash
# Local run
SPRING_PROFILES_ACTIVE=liquibase make run

# Docker Compose (temporary override)
SPRING_PROFILES_ACTIVE=liquibase docker compose up -d app
```

Alternatively, `api/src/main/resources/application-liquibase.yml` is already present and sets `spring.liquibase.enabled=true`.

## DSLContext: auto-config, codegen, and troubleshooting

- You do NOT need to define a `@Bean` for `DSLContext`. Spring Boot autoconfigures it via `spring-boot-starter-jooq` when a `DataSource` is present. In this codebase, `RepositoryConfig.teamDao(DSLContext)` correctly relies on the auto-provided bean.
- jOOQ code generation is a build-time task in the `model` module and is unrelated to runtime injection of `DSLContext`. Codegen runs during `generate-sources` (e.g., `make codegen` / `make model-compile`) based on your DB schema; the generated classes are then consumed at runtime but don’t affect auto-wiring.
- If `spring.datasource.*` is misconfigured or the JDBC driver is missing, `DSLContext` won’t be created and the app will fail at startup.

Quick checklist if startup fails or `curl` can’t connect:

1. Ensure the app is actually running

   - Use:
     - `make run-app` to start Postgres (Compose) and run the JAR, or
     - `make run` if your local Postgres is already up.
   - Healthcheck: `curl http://localhost:${PORT:-8080}/actuator/health` should return `{"status":"UP"}` when the app is ready.

2. Verify datasource settings

   - The app reads `SPRING_DATASOURCE_URL` directly, or builds it from `DB_*` (host defaults to `localhost`, port defaults to `55432`).
   - Confirm env variables (or `.env` loaded by Makefile/Compose): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
   - Example URL: `jdbc:postgresql://localhost:55432/ligitabl`.

3. Confirm the Postgres driver is on the classpath

   - The API includes `org.postgresql:postgresql`; building with `mvn -pl api -am package` produces a runnable JAR with the driver.

4. Check logs for autoconfiguration hints

   - On failure you’ll typically see messages like "Failed to configure a DataSource" or "No qualifying bean of type 'org.jooq.DSLContext'". These indicate the datasource couldn’t be created (bad URL/creds/DB down) or the driver is missing.

5. Port already in use
   - If the server can’t start on `8080`, set a different port: `PORT=8081 make run-app` and curl `http://localhost:8081/...`.

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelogs are present. Runtime Liquibase is disabled by default; enable with the `liquibase` Spring profile or use `make migrate`.
- Use the per-environment files instead of a single `.env`: `.env.test` (default), `.env.dev`, `.env.prod`.
