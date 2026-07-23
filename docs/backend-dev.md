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

Remember-me uses Spring Security’s `PersistentTokenBasedRememberMeServices`: tokens live server-side in the `persistent_logins` table (via `JdbcTokenRepositoryImpl`) and the cookie only carries a series/token pair. It covers **three entry points**: form login, registration, and Google OAuth.

**Why persistent tokens instead of the hash-based cookie** (`TokenBasedRememberMeServices`, used previously):

- The hash-based cookie is signed with the user’s **password hash** — Google-only accounts have no password, so it couldn’t safely cover OAuth logins at all.
- DB tokens are per-device revocable, rotate on every use, and detect stolen-cookie reuse (series match + token mismatch ⇒ all tokens for the user are purged).
- A leaked signing key no longer allows forging cookies for arbitrary users.

The `persistent_logins` table deliberately **breaks the `t_*`/`c_*` naming convention** — `JdbcTokenRepositoryImpl`’s SQL hardcodes the standard Spring Security table/column names (migration: `20260723_1_persistent_logins.yaml`).

#### Why custom wiring was needed

Login and registration are handled by a **custom controller** (`AuthController`) rather than Spring Security’s native form login processing URL (`/auth/login/process`). This means the remember-me cookie is **not** set automatically — the filter chain never sees a successful form login event.

To make it work, `RememberMeServices` is exposed as a shared `@Bean` in `SecurityConfig` and injected into `AuthController`. After `authenticateUser()` sets the session, both the login and register handlers explicitly call:

```java
rememberMeServices.loginSuccess(request, response, authentication);
```

`loginSuccess` reads the `rememberMe` request parameter internally and only sets the cookie if the checkbox was checked.

#### The checkbox (login + register forms)

The “Remember me” checkbox is a **form-bound field** (`rememberMe` on `LoginForm`/`RegisterForm`, default `true`), rendered with `th:field="*{rememberMe}"`. Two reasons it is not a raw `<input checked>`:

- A raw input resets to its static default when validation fails and the form re-renders; binding it makes the submitted state round-trip like every other field.
- `th:field` emits the hidden `_rememberMe` companion field, so an explicit “unchecked” survives the round-trip too.

The services’ parameter is set to `rememberMe` (not the Spring default `remember-me`) to match what `th:field` emits. The **cookie** is still named `remember-me` — parameter name and cookie name are independent, which is why logout’s `deleteCookies("JSESSIONID", "remember-me")` is unchanged.

#### OAuth (Google) remembrance

The Google redirect flow has **no checkbox to carry a choice**, so OAuth logins are treated as always-remember — the common industry pattern, since “Sign in with Google” implies “this site keeps knowing me.” A second bean, `oauth2RememberMeServices` (`alwaysRemember = true`, same key and token store as the form-login bean), is called from `OAuth2AuthenticationSuccessHandler.handleNormalLogin`. Sharing the key/store means the single remember-me filter validates cookies from either source.

This means **there is no session-only option for Google sign-in** — the checkbox on the login/register forms only governs form-based auth. A Google user who wants to end the trust logs out, which deletes the DB token and cookie. If an opt-out were ever wanted, the choice would have to be captured *before* the redirect (e.g. a “keep me signed in” checkbox next to the Google button, stashed in the HTTP session or as a query param on `/oauth2/authorization/google`) and read back in the success handler to decide whether to call `loginSuccess`.

Details that matter here:

- `loginSuccess` is called with the **web authentication** built by `establishSessionAuthentication` (name = email), not the raw `OAuth2AuthenticationToken` (name = Google subject id) — auto-login later resolves the cookie’s username via `WebUserDetailsService.loadUserByUsername`, which looks up by email.
- `WebUserDetailsService` guards against `null` passwords (Google-only accounts) — without the guard, remember-me auto-login for those users would NPE.
- The account-**linking** flow does not touch remember-me: the user is already logged in there.
- Auto-logins bypass the controllers entirely (`RememberMeAuthenticationFilter`), so last-login tracking for them lives in `RememberMeLoginListener`.

#### Configuration (see `application.yml`)

- `ligitabl.security.remember-me.key` — key for the `RememberMeAuthenticationToken` (no longer signs cookie contents)
- `ligitabl.security.remember-me.token-validity-seconds` — token lifespan (default 14 days)

Defaults are **dev-safe** and must be overridden in production using env vars:

- `REMEMBER_ME_KEY`
- `REMEMBER_ME_TOKEN_VALIDITY_SECONDS`

#### Migration from the hash-based scheme

Cookies issued by the old `TokenBasedRememberMeServices` fail to decode against the persistent store, get cancelled, and the user simply logs in once more — no cleanup or backfill needed.

### Navbar context

Navbar labels/links are computed in `NavbarControllerAdvice`, using the custom principal when present to avoid extra DB lookups.

### Logout

The UI logs out via a plain **GET** link to `/auth/logout`, which hits the custom `AuthController.logout` — not Spring’s `LogoutFilter` (that only matches POST while CSRF is enabled). The controller calls `request.logout()`, which runs the same configured logout handlers the filter would: clearing the security context, invalidating the session, cancelling the remember-me cookie, and **revoking the remember-me token in the DB**.

**Ordering constraint**: `request.logout()` must run *before* the controller clears the security context. The logout handlers read the `Authentication` from the context, and `PersistentTokenBasedRememberMeServices` silently skips DB-token revocation when it gets `null` — the cookie would still be cancelled, so logout *looks* fine, but a stolen copy of the cookie would stay valid until expiry.

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

### Sharing test fixtures across modules (model ↔ api)

`model` publishes a `tests` classifier jar (via a `maven-jar-plugin` `test-jar` execution in
`model/pom.xml`) so `api` tests can reuse test-only fixtures defined in `model/src/test/java`
instead of duplicating them. `api/pom.xml` depends on it as:

```xml
<dependency>
    <groupId>com.ligitabl</groupId>
    <artifactId>ligitabl-model</artifactId>
    <version>${project.version}</version>
    <classifier>tests</classifier>
    <scope>test</scope>
</dependency>
```

Example: `CompetitionPhaseFixtures` (`model/src/test/java/com/ligitabl/model/domain/`) holds the
Premier League's real phase structure (mirrors `seed/src/main/resources/seeding/competition.yaml`)
and is reused by both `PhaseRulesTest` in `model` and several contest-renewal tests in `api`.

**Caveat — this needs model to have reached `test-compile` at least once since the last clean.**
Any reactor build whose requested phase is `test` or later (`mvn -pl api -am test`, `make
test-api-core`, etc.) satisfies this itself, since `model` runs through its own `test-compile`
first. But `compile`-only invocations — notably `make run-api-fast` / `run-api-fast-model` — never
make `model` reach `test-compile`, so on a fresh checkout or right after `mvn clean` they'll fail
with `Could not find artifact com.ligitabl:ligitabl-model:jar:tests`. If you hit that, run
`mvn -pl model install` (or any full `test`/`install` build) once, or use `make
run-api-fast-model`, which already does a full `install` of `model`.

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

## SpEL string literals: don't use HTML entities for quotes

**Issue**: Using an HTML entity like `&#39;` inside a `th:text`/`th:attr` SpEL expression to embed a literal
apostrophe throws `org.springframework.expression.spel.SpelParseException: ... EL1044E: Unexpectedly ran out of
input` at template-parse time.

**Root cause**: The attribute value is parsed as XML/HTML first, which decodes `&#39;` to a literal `'` character
*before* Thymeleaf hands the string to the SpEL parser. That literal `'` is then read by SpEL as closing the
string literal early, leaving the rest of the expression dangling.

```html
<!-- WRONG: &#39; decodes to ' before SpEL sees it, breaking the string literal -->
<span th:text="${match.minute + '&#39;'}">90'</span>
```

**Fix**: use SpEL's own escaping for a quote inside a single-quoted string literal — two apostrophes (`''`)
represent one literal apostrophe. Write the raw characters directly (no HTML entity):

```html
<!-- CORRECT: '''' = open quote + escaped quote ('') + close quote = one literal apostrophe -->
<span th:text="${match.minute + ''''}">90'</span>
```

This only surfaces once the branch guarding the expression actually evaluates to true (e.g. an `th:if` gating a
`th:text` on the same or a child element) — a template can pass every existing test and still throw this in
production the first time real data hits that branch. If you need to sanity-check a SpEL expression in isolation
without spinning up the app, `org.springframework.expression.spel.standard.SpelExpressionParser` can evaluate it
directly against a plain object.

### Related Files

- Example: `api/src/main/resources/templates/matches.html` (LIVE badge minute/injury-time rendering)

## HTMX + Alpine event listener reliability

When wiring loading indicators or other request-lifecycle UI around HTMX swaps, do not assume Alpine's `.window`
modifier will reliably catch HTMX events.

### What was observed

- HTMX dispatches lifecycle events on the target element (for example `#prediction-page`) with bubbling enabled.
- Those events can bubble up through the DOM: element -> body -> document -> window.
- HTMX also dispatches a duplicate event on `document.body`.
- In practice, the `document.body` dispatch is the most reliable hook point.
- Alpine `.window` listeners can miss HTMX events depending on where the event was dispatched and whether that
  particular dispatch reaches `window`.

### Reliability order

Use these options in this order of preference:

1. `document.body.addEventListener(...)` — most reliable for HTMX lifecycle hooks
2. Alpine listener on a parent element close to the HTMX target — acceptable when the component owns the target
3. Alpine `.window` listener — least reliable; avoid for important HTMX state transitions

### Recommended pattern

For reusable loading indicators, bind directly in JavaScript on `document.body`:

```js
document.body.addEventListener("htmx:beforeRequest", (event) => {
  // show loader
});

document.body.addEventListener("htmx:afterSwap", (event) => {
  // hide loader
});

document.body.addEventListener("htmx:responseError", (event) => {
  // hide loader
});
```

If the behavior is tightly scoped to one fragment, filter by `event.detail.target`, request path, or another
HTMX detail field rather than relying on global state.

### Why the leaderboard spinner worked

The leaderboard loading state was attached on an Alpine component that was already the parent of the HTMX swap
target, so the event was observed close to where it fired. That is safer than depending on the event to reach
`window`.

### Current repo example

See `api/src/main/resources/static/js/ligitabl.js` for the pure JavaScript approach used after prediction-page
round navigation proved unreliable with Alpine `.window` listeners.

## Manually driving a running instance (curl + psql) — end-to-end verification without a browser

When you can't drive a real browser (headless/CI environment, no `chromium-cli`/Playwright available) but still
need to prove a feature works — not just that it compiles or passes unit tests — this is the pattern: a real
`spring-boot:run` instance, authenticated via `curl` with a cookie jar, with test data set up and verified
directly against Postgres via `psql`. This is how the admin users list (`/admin/users`) and its delete/batch-
delete/last-login-tracking features were verified (see `.art/task_65.md`).

### Run on an alternate port if a dev server is already up

`make run-api-fast-model` (the right one-liner for normal iteration — see "Typical dev/test flow" above) hardcodes
the port from `.env.test`'s `PORT`. If you already have a dev server bound to it, don't kill it — someone (maybe
you, in another terminal) may be relying on it. Instead run a second instance on a different port, loading the
same env files by hand since you're bypassing `make`:

```bash
set -a
source .env.test
[ -f .env.test.local ] && source .env.test.local   # secrets (e.g. GOOGLE_CLIENT_ID) live here, gitignored
set +a

mvn -q -f api/pom.xml -Dspring-boot.run.mainClass=com.ligitabl.api.LigitablApplication \
  -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=false" \
  -Dspring-boot.run.arguments="--server.port=8082" \
  -DDB_HOST=localhost -DDB_PORT=$DB_PORT -DDB_NAME=$DB_NAME -DDB_USER=$DB_USER -DDB_PASSWORD=$DB_PASSWORD \
  org.springframework.boot:spring-boot-maven-plugin:run > /tmp/app.log 2>&1 &
```

Wait for real readiness instead of a fixed `sleep`:

```bash
until grep -qE "Tomcat started on port|APPLICATION FAILED TO START" /tmp/app.log 2>/dev/null; do sleep 2; done
```

**Gotcha:** after any change to a `model` module class (new repo method, new domain type), `mvn compile` is not
enough — `spring-boot:run` run this way resolves `model` as a packaged `~/.m2` dependency, not a reactor sibling,
so a stale jar is invisible to it. Run `mvn -DskipTests -pl model,api -am install` (not `compile`) first. This is
exactly what `make run-api-fast-model` already does for you under normal `make`-driven iteration — the manual
recipe above only exists for the "don't disturb an already-running dev server" case.

### Log in with a cookie jar and the real CSRF token

Spring Security CSRF protection means a raw `curl -d ...` POST to `/auth/login` will 403 without a valid token.
Fetch the login page first (capturing cookies), scrape the hidden `_csrf` field out of the form, then POST with
both:

```bash
CSRF=$(curl -s -c cookies.txt http://localhost:8082/auth/login \
  | grep -oE 'name="_csrf" value="[^"]*"' | sed -E 's/.*value="([^"]*)"/\1/')

curl -s -i -c cookies.txt -b cookies.txt \
  -d "email=someone@example.com" -d "password=..." -d "_csrf=$CSRF" \
  http://localhost:8082/auth/login
```

A successful login is a `302` redirect (e.g. to `/my-table`); a failed one re-renders the login page as `200`
with the form again — check for that, not just the HTTP status, since a wrong field name (the login form's email
field is literally named `email`, not `username`) silently "succeeds" with a 200 that's actually the login page.

Once logged in, every subsequent authenticated request just needs `-b cookies.txt`. For POST endpoints past this
point, pull a fresh CSRF token out of the already-fetched page's `<meta name="_csrf" content="...">` tag instead
of re-fetching the login page:

```bash
CSRF=$(grep -oE 'name="_csrf" content="[^"]*"' some_page.html | sed -E 's/.*content="([^"]*)"/\1/')
```

### Test htmx fragment endpoints directly

Controllers that branch on the `HX-Request` header (see `GetLeaderboardController`, `AdminUserController`) can be
hit directly with `curl -H "HX-Request: true"` to get back just the fragment, without needing an actual htmx
client:

```bash
curl -s -b cookies.txt -H "HX-Request: true" "http://localhost:8082/admin/users?page=2&size=10"
```

### Set up test accounts/roles/data via `docker exec ... psql`, and always plan the revert

The test Postgres container is reachable directly — this is the fastest way to grant a role, set a known
password, or seed rows that would otherwise require walking through unrelated UI flows:

```bash
# Grant a role (idempotent)
docker exec ligitabl-db psql -U ligitabl -d ligitabl_test -c \
  "INSERT INTO t_user_role (fk_user_id, c_role) VALUES ('<uuid>', 'ADMIN') ON CONFLICT DO NOTHING;"

# Set a known bcrypt password on an existing test user, so you can log in as them
htpasswd -bnBC 10 "" 'TestPass123!' | tr -d ':\n'   # prints a $2y$ bcrypt hash — Spring's
                                                      # BCryptPasswordEncoder accepts $2a/$2b/$2y alike
docker exec ligitabl-db psql -U ligitabl -d ligitabl_test -c \
  "UPDATE t_user SET c_password_hash = '<hash>' WHERE pk_id = '<uuid>';"

# Verify state directly, before and after an action under test
docker exec ligitabl-db psql -U ligitabl -d ligitabl_test -c \
  "SELECT count(*) FROM t_entry WHERE fk_user_id = '<uuid>';"
```

**Before mutating anything this way, capture what you're about to overwrite** (a `SELECT` first) so you can
revert it — or explicitly disclose that you can't. Overwriting `c_password_hash` on a real seed user without
reading the original value first means you can't put it back; that's fine for throwaway `ENV=test` data, but say
so rather than silently leaving it changed. Always revert role grants (`DELETE FROM t_user_role WHERE ...`) and
any other test-only mutation once verification is done — this is `ENV=test`, not a sandbox that resets itself.

This is also how to prove a live bug that static analysis wouldn't catch: a batch-delete cascade was verified
this way and caught a real `DataIntegrityViolationException` from a second-level FK (`t_round_result` →
`t_round_submission` → `t_user`) that a straightforward `information_schema` query scoped to direct `t_user`
foreign keys had missed. Query the *full* dependency graph, not just one hop, when verifying a cascade delete:

```sql
SELECT tc.table_name AS referencing_table, kcu.column_name, ccu.table_name AS referenced_table, rc.delete_rule
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND ccu.table_name IN ('t_user', 't_entry', 't_round_submission', /* ...every table you delete from... */);
```

### Testing remember-me / cookie-only auto-login

To prove an `InteractiveAuthenticationSuccessEvent`-driven code path (remember-me auto-login) actually fires, and
not just the easy explicit-login path: log in once with `remember-me=true` to obtain the `remember-me` cookie,
then make a **second** request with the `JSESSIONID` stripped from the cookie jar (but the `remember-me` cookie
kept) — this forces `RememberMeAuthenticationFilter` to reconstitute the session from the cookie alone, with no
active session to fall back on:

```bash
grep -v "JSESSIONID" cookies.txt > cookies_remember_me_only.txt
curl -s -i -b cookies_remember_me_only.txt http://localhost:8082/my-table
```

### Stop the alternate-port instance when done

```bash
lsof -ti tcp:8082 | xargs -r kill
```

## Notes

- Spring Boot 3.5.3 (Java 21)
- Liquibase changelogs are present. Runtime Liquibase is disabled by default; enable with the `liquibase` Spring profile or use `make migrate`.
- Use the per-environment files instead of a single `.env`: `.env.test` (default), `.env.dev`, `.env.prod`.
