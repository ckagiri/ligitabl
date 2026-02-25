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

## Dependency inspection (dependency:tree)

When investigating a vulnerability report or a weird classpath mismatch, use Maven's dependency tree to confirm
the **resolved** version.

Examples:

```bash
# Show the resolved PostgreSQL JDBC driver version pulled into the API module
mvn -pl api -am -DskipTests dependency:tree -Dincludes=org.postgresql:postgresql -Dverbose

# If you need to see it in context (no filter), drop -Dincludes and/or -Dverbose
mvn -pl api -am -DskipTests dependency:tree
```

Tip: if the output is huge, pipe it through `rg`:

```bash
mvn -pl api -am -DskipTests dependency:tree -Dincludes=org.postgresql:postgresql -Dverbose | rg "org\\.postgresql:postgresql"
```

## Running the API without a DB

The default developer workflow assumes a database (local Docker Compose or Testcontainers in tests).

## Security & Auth (Web + API)

This app has **two security flows**:

- **API** (`/api/**`): stateless JWT authentication
- **Web UI** (non-`/api/**`): session-based form login

### Web UI auth flow

- Login/register pages are served by the web controller.
- On successful login/registration, we **manually authenticate** and store a `WebUserDetails` principal in the session.
- The custom principal includes: `userId`, `publicId`, `email`, and `displayName`.

### Remember-me

The login form has a “Remember me” checkbox wired to Spring Security’s `TokenBasedRememberMeServices`.

#### Why custom wiring was needed

Login is handled by a **custom controller** (`AuthController.@PostMapping(“/auth/login”)`) rather than Spring Security’s native form login processing URL (`/auth/login/process`). This means the remember-me cookie is **not** set automatically after login — the filter chain never sees a successful form login event.

To make it work, `RememberMeServices` is exposed as a shared `@Bean` in `SecurityConfig` and injected into `AuthController`. After `authenticateUser()` sets the session, the controller explicitly calls:

```java
rememberMeServices.loginSuccess(request, response, authentication);
```

`TokenBasedRememberMeServices` reads the `remember-me` request parameter internally and only sets the cookie if the checkbox was checked.

#### Configuration (see `application.yml`)

- `ligitabl.security.remember-me.key` — signing key for the remember-me cookie (SHA256)
- `ligitabl.security.remember-me.token-validity-seconds` — cookie lifespan (default 14 days)

Defaults are **dev-safe** and must be overridden in production using env vars:

- `REMEMBER_ME_KEY`
- `REMEMBER_ME_TOKEN_VALIDITY_SECONDS`

**Cookie-based remember-me (current):**

- No database storage; simple and fast.
- If the key leaks, cookies can be forged until rotated.
- Cannot revoke a single device.

**Persistent token store (optional alternative):**

- Stores remember-me tokens in DB.
- Allows per-device revocation and detects token theft (series mismatch).
- Slightly more complexity and DB reads.

### Navbar context

Navbar labels/links are computed in `NavbarControllerAdvice`, using the custom principal when present to avoid extra DB lookups.

### Logout

Logout clears the security context, invalidates the session, and removes the remember-me cookie.

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

### Troubleshooting: `clean` + missing generated sources

The `model` module includes generated jOOQ sources under `model/target/generated-sources/jooq`.

- By default, jOOQ codegen is skipped (fast builds).
- A full `clean` can delete generated sources; if codegen is still skipped afterwards, compilation may fail if
  any code imports `com.ligitabl.model.db.*`.

If you hit build failures that *look* like classpath problems (e.g. `cannot access AbstractModel`, missing
`SeasonPrediction.getId()`, or builder `.id(...)` not found), see the dedicated playbook in:

- [debugging-tests.md](./debugging-tests.md) → “Build failures that look like classpath issues (stale bytecode)”

If you want a one-liner that brings up Postgres via Docker Compose, runs Liquibase migrations, and generates jOOQ
classes for local development, use:

```bash
make model-codegen-local
```

## Troubleshooting: run-api / jOOQ missing classes

Symptoms:

- `package com.ligitabl.model.db.tables does not exist`
- `cannot find symbol ... Record`
- `NoSuchMethodError` after code changes (stale model classes)

Root cause:

- jOOQ sources weren’t generated for the active DB/port, or a stale build is on the classpath.

Fix checklist (safe, ordered):

1. Ensure DB is up for the current env (`ENV=test` default) and that migrations are applied.
2. Regenerate jOOQ from the same DB and port configured in the env file.
3. Rebuild API with the jOOQ-enabled profile so the model module compiles against fresh sources.

If you still see missing `com.ligitabl.model.db` classes, confirm:

- The DB has tables (not just `databasechangelog*`).
- jOOQ generated files exist under `model/target/generated-sources/jooq`.
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` match your running DB.

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

The repo includes a headless match importer workflow (Premier League only).

Make targets:

```bash
make import-competition COMP=PL
make import-pl
make import-pl-with-seed
````

Notes:

- These Make targets use the selected env file (`.env.test`, `.env.dev`, or `.env.prod`) plus the optional local override for that env.
- Set your token in `.env.dev.local` (preferred) as `API_FOOTBALL_DATA_KEY=...` (or `FOOTBALL_DATA_API_TOKEN=...`).
- By default, importer targets start DB (compose), build the API jar, then run the workflow in headless mode (no implicit seeding).
  - If you need reference data, run `make db-seed` first or use `make import-pl-with-seed`.

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

## Spring Boot DevTools: hot reload for templates and static resources

Spring Boot DevTools auto-restarts the application when class files change. However, in a multi-module Maven project, DevTools restart can fail because the `RestartClassLoader` cannot resolve classes from dependent modules (e.g., `model`).

**Symptom:**

```
org.springframework.beans.factory.BeanDefinitionStoreException: Failed to parse configuration class
Caused by: java.lang.ClassNotFoundException: ContestRepo
    at org.springframework.boot.devtools.restart.classloader.RestartClassLoader.loadClass
```

This happens when editing HTML/JS/CSS files triggers a DevTools restart — classes in the `model` module are loaded by the base classloader but the `RestartClassLoader` can't find them during restart.

**Fix:**

Disable DevTools restart entirely. Templates and static resources don't need a restart — Thymeleaf already reloads templates automatically when caching is disabled, and static resources are served directly from disk:

```properties
# Already configured
spring.thymeleaf.cache=false
spring.web.resources.cache.cachecontrol.no-cache=true

# Disable DevTools restart — RestartClassLoader can't resolve model module classes.
# Templates and static resources reload without restart anyway.
spring.devtools.restart.enabled=false
```

**Why not `additional-exclude`?** We initially tried `spring.devtools.restart.additional-exclude=**/*.html,**/*.js,**/*.css` but it was unreliable — DevTools still triggered restarts on file changes in some cases.

**How it works:**

- `spring.thymeleaf.cache=false` — Thymeleaf re-reads template files on every request (no restart needed)
- `spring.web.resources.cache.cachecontrol.no-cache=true` — Browser always fetches fresh static resources
- `spring.devtools.restart.enabled=false` — DevTools restart is fully disabled, avoiding the ClassNotFoundException

**Development workflow:**

- Edit HTML/JS/CSS → refresh browser → changes appear immediately (no restart)
- Edit Java files → must manually restart the application (`make run-api-fast`)

**Enabling DevTools restart (opt-in):**

If you're only editing Java files in the `api` module and want auto-restart on class changes:

```bash
make run-api-fast DEVTOOLS_RESTART=true
```

Note: this may fail with `ClassNotFoundException` for classes in the `model` module. Use the default (`false`) if you see that error.

**Run targets:**

```bash
make run-api-fast     # Start DB, compile, run (skips migration + jOOQ codegen)
make run-api-fastest  # Start DB, run (assumes already compiled)
```

## Thymeleaf templates: boolean conditionals best practices

**TL;DR**: When using `th:if` with `th:replace`, wrap the fragment inclusion in a `<th:block>` element. Additionally, use explicit boolean comparisons (`== true`, `== false || == null`) for clarity and to avoid SpEL evaluation quirks.

### The Problem

We discovered that mutually exclusive template sections were rendering simultaneously, causing duplicate content on the page. The root causes were:

1. **Attribute evaluation order**: When `th:if` and `th:replace` are on the same element, Thymeleaf's attribute processing order can cause unexpected behavior where both mutually exclusive sections render
2. **Autoboxing**: When Java primitive `boolean` values are added to Spring's `Model`, they get autoboxed to nullable `Boolean` objects
3. **SpEL evaluation quirks**: The `!` negation operator and implicit truthiness checks can behave unpredictably with autoboxed Booleans

### What We Observed

Before the fix, templates using implicit boolean checks had both mutually exclusive sections rendering:

```html
<!-- PROBLEMATIC: Both sections rendered at the same time -->
<div th:if="${isGuest}">...</div>
<!-- Rendered when shouldn't -->
<div th:unless="${isGuest}">...</div>
<!-- Also rendered -->

<!-- Same issue with negation -->
<div th:if="${isCurrentRound}">...</div>
<!-- Rendered -->
<div th:if="${!isCurrentRound}">...</div>
<!-- Also rendered! -->
```

This caused:

- Duplicate prediction tables appearing on the page
- Both current round and historical round views rendering simultaneously
- Confusing UI where guest and authenticated user views both appeared

### The Solution

**Primary Fix: Use `<th:block>` for conditionals with `th:replace`**

When you need to conditionally include a fragment, wrap the `th:replace` element in a `<th:block>` tag:

```html
<!-- CORRECT: th:block wrapper ensures conditional is evaluated before fragment replacement -->
<th:block th:if="${isCurrentRound == true}">
  <div
    th:replace="~{fragments/prediction-table :: interactive-table(alwaysHoverable=false)}"
  ></div>
</th:block>

<th:block th:if="${isCurrentRound == false || isCurrentRound == null}">
  <div
    th:replace="~{fragments/prediction-historical-view :: historical-view(...)}"
  ></div>
</th:block>
```

**Why this works:** `<th:block>` is a Thymeleaf-only element that doesn't render to HTML. It ensures the `th:if` conditional is evaluated before the `th:replace` fragment replacement happens, preventing both sections from rendering.

**Secondary Fix: Use explicit boolean comparisons**

For clarity and defensive programming, use explicit boolean comparisons:

```html
<!-- GOOD: Explicit comparisons -->
<div th:if="${isGuest == true}">
  <!-- Guest-only content -->
</div>

<div th:if="${isGuest == false || isGuest == null}">
  <!-- Non-guest content -->
</div>
```

### Why This Works

Explicit comparisons force a clear three-way distinction:

- `true` - explicitly true
- `false` - explicitly false
- `null` - explicitly handle null cases

This avoids SpEL's implicit truthiness evaluation and ensures only one section of mutually exclusive conditionals renders.

### Additional Examples

**Pattern for mutually exclusive fragment inclusions:**

```html
<!-- CORRECT: Use th:block wrapper for conditionals with th:replace -->
<th:block th:if="${condition == true}">
  <div th:replace="~{fragments/section-a :: content}"></div>
</th:block>

<th:block th:if="${condition == false || condition == null}">
  <div th:replace="~{fragments/section-b :: content}"></div>
</th:block>

<!-- INCORRECT: th:if and th:replace on same element can cause issues -->
<div th:if="${condition}" th:replace="~{fragments/section-a :: content}"></div>
<div th:if="${!condition}" th:replace="~{fragments/section-b :: content}"></div>
```

**Negation with compound conditions:**

```html
<!-- AVOID: Implicit negation -->
<div th:if="${isCurrentRound && !isUserNotFound}">
  <!-- PREFER: Explicit boolean checks -->
  <div
    th:if="${isCurrentRound == true && (isUserNotFound == false || isUserNotFound == null)}"
  ></div>
</div>
```

### When to Apply This

**Always use `<th:block>` when:**

- You have `th:if` or `th:unless` on an element that also has `th:replace` or `th:include`
- You need mutually exclusive fragment inclusions
- You want to ensure conditionals are evaluated before fragment replacement

**Use explicit boolean comparisons when:**

- You have mutually exclusive template sections (guest vs. authenticated, current vs. historical, etc.)
- Boolean model attributes control rendering logic
- You have complex conditional logic with multiple boolean checks
- You're using the `!` negation operator or `th:unless`

### Related Files

- Template example: `api/src/main/resources/templates/predictions.html`
- Controller setting booleans: `UserPredictionsController.java` (line 270)

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelogs are present. Runtime Liquibase is disabled by default; enable with the `liquibase` Spring profile or use `make migrate`.
- Use the per-environment files instead of a single `.env`: `.env.test` (default), `.env.dev`, `.env.prod`.
