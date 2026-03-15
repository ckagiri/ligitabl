# LigiTabl

A football league prediction game where players predict league standings each round and earn points based on
accuracy. Players can adjust predictions periodically via team swaps. Scores are calculated when rounds are
finalized by comparing predictions to computed league standings.

Live demo: https://ligipredictor.com/

## Tech stack

- **Backend**: Spring Boot 3.5.3, Java 21
- **Database**: PostgreSQL 16, jOOQ 3.20 (type-safe queries), Liquibase (migrations)
- **Frontend**: Thymeleaf (server-side rendering), Alpine.js (interactivity), HTMX (partial page updates), Tailwind CSS
- **Auth**: Session-based (web UI) + JWT (REST API), remember-me tokens
- **Data**: Football-Data.org API integration with automated sync
- **Testing**: JUnit 5, Testcontainers (DB-backed integration tests), WireMock (API mocking); use cases have unit and integration test coverage
- **Build**: Maven multi-module, safety-first Makefile

## Design principles

- Mobile-first, responsive UI
- Server-side rendering with progressive enhancement — Thymeleaf renders complete pages; Alpine.js and HTMX layer in interactivity without requiring JS for basic use
- API-driven match data (Football-Data.org)
- Automated round progression and leaderboard points computation
- Explicit error handling — use cases return `Either<Error, Result>` instead of throwing; errors are handled at boundaries, not buried
- Safety-first database operations — Makefile enforces named environments (`test`, `dev`, `prod`) with no silent fallbacks; destructive operations require confirmation
- Type-safe database access — jOOQ generates compile-time query types from the schema; raw SQL strings are avoided

## Requirements

- Java 21
- Maven 3.9+
- Docker + Docker Compose (recommended for local Postgres)
- PostgreSQL 16

## Quick start

```bash
# 1. Set up environment files
cp env.test.template .env.test

# 2. Start DB, run migrations, build, and launch
make run-api

# 3. Verify
curl http://localhost:8081/api/status
```

For faster iteration (skips migration + jOOQ codegen):

```bash
make run-api-fast
```

## Repo layout

```
api/          Spring Boot application (REST + web controllers, use cases, scheduling, security)
model/        Database model (Liquibase changelogs, jOOQ-generated types, domain objects)
seed/         Seeding CLI for loading reference/demo data into the database
jooq-codegen/ Helper module for jOOQ code generation against the current schema
scripts/      End-to-end and smoke scripts (auth checks, seeding checks)
docs/         Developer documentation
```

## Architecture

The project follows clean architecture with use-case-driven design:

- **REST API** (`/api/**`) -- stateless JWT auth, JSON responses
- **Web UI** (everything else) -- session-based auth, Thymeleaf + HTMX
- **Use cases** encapsulate business logic, return `Either<Error, Result>` for explicit error handling
- **jOOQ repositories** provide type-safe database access
- **Schedulers** handle automated match sync and round advancement

### Match sync

Matches are synced from Football-Data.org on a dynamic schedule:

| Condition           | Frequency        |
| ------------------- | ---------------- |
| Live matches        | Every 90 seconds |
| Kickoff <= 10 min   | Every 1 minute   |
| Kickoff <= 60 min   | Every 10 minutes |
| Kickoff < 6 hours   | Every 1 hour     |
| No upcoming matches | Every 12 hours   |
| Season complete     | Every 24 hours   |

When all matches in a round complete, finalization triggers automatically.

## Frontend assets

CSS and JavaScript assets are built through Vite. All frontend tooling lives in `api/` alongside the Spring Boot application.

### Toolchain

| Tool | Version | Role |
| ---- | ------- | ---- |
| Vite | ^7 | Bundler & dev server |
| Tailwind CSS | ^3 | Utility-class CSS generation |
| PostCSS + Autoprefixer | — | CSS post-processing |
| Terser | ^5 | JS/CSS minification (production only) |

### How it works

- **CSS entry point**: `api/src/main/resources/static/css/main.css` — contains Tailwind directives
- **JS entry point**: `api/src/main/resources/static/js/ligitabl.js` — app JavaScript source bundled by Vite
- **Generated CSS**: `api/src/main/resources/static/dist/css/main.css`
- **Generated JS**: `api/src/main/resources/static/dist/js/app.js`
- **Template usage**: `base.html` loads assets from `/dist/css/main.css` and `/dist/js/app.js`
- **Content scanning**: Tailwind scans all Thymeleaf templates (`templates/**/*.html`) and JS files (`static/js/**/*.js`) for class names; unused utilities are purged in production
- **Minification**: Terser runs only in production mode; `drop_console` and `drop_debugger` are on by default, and the JS bundle is mangled in production

### npm scripts

```bash
cd api

npm run build:prod   # One-off production build (minified, no console/debugger)
npm run build:dev    # One-off development build (unminified)
npm run dev          # Watch mode — rebuilds on every template/CSS change
```

Run `build:prod` before packaging a JAR or building the Docker image so the compiled CSS and JS bundles are included. The CI pipeline runs `mvn package` which picks up whatever is already in `static/dist/`; regenerate before committing if you change Tailwind classes or frontend JavaScript.

### Adding new utilities or JS changes

Tailwind only emits classes it finds in the scanned files. If you add a new utility class directly in a template, or change code in `static/js/ligitabl.js`, rerun `npm run build:dev` (or keep the `dev` watcher running) so the generated files in `static/dist/` stay in sync.

## Key Make targets

```bash
# Development
make run-api              # Start DB, migrate, build, run
make run-api-fast         # Start DB, compile, run (skip migrate + codegen)
make run-api-fastest      # Start DB, run (assumes already compiled)
make dev-reset            # Reset DB, migrate, codegen, seed reference data

# Testing
make test-api-core        # Core API tests (skip integration tests)
make test-api-it          # DB-backed integration tests (Testcontainers)
make test-api-all         # Full API test suite
make test-all             # Full project test suite

# Database
make migrate              # Run Liquibase migrations
make codegen              # Regenerate jOOQ sources
make db-seed              # Seed reference data (competitions, teams, rounds)
make db-seed-demo         # Seed demo league data

# Data import
make import-pl            # Import Premier League matches
make import-pl-with-seed   # Import Premier League after seeding reference data

# Code quality
make format               # Format Java sources (Palantir Java Format)
make format-check         # Check formatting

# Docker
make docker-build         # Build Docker image
make compose-up           # Start app + Postgres
```

## Environment management

The Makefile enforces explicit environments with no silent fallbacks:

- `ENV=test` (default) -- safe for experiments and destructive operations
- `ENV=dev` -- daily development DB
- `ENV=prod` -- production (requires `PROD_CONFIRMED=yes`)

Environment files: `.env.test`, `.env.dev`, `.env.prod` (from templates).

```bash
make env-info             # Show current environment config
make env-check            # Validate environment files exist
```

## CI/CD

The pipeline is defined in [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) and runs three sequential jobs.

### Jobs

| Job | Trigger | What it does |
| --- | ------- | ------------ |
| **Run Tests** | Every push & PR to `main` | Spins up a Postgres service container, runs Liquibase migrations, generates jOOQ sources via `-Pwith-jooq`, runs the full Maven test suite |
| **Build & Push** | Push to `main` or `release` (after tests pass) | Builds the Docker image with Buildx (layer cache via GHA) and pushes two tags to Docker Hub: `api-latest` and `api-<sha>` |
| **Deploy** | Push to `main` or `release` (after image is pushed) | SSH/SCP into the Digital Ocean droplet, writes `.env.prod` from GitHub secrets, pulls the new image, does a zero-downtime `docker compose` swap, and warns if the DB has no seed data |

### Secrets required

| Secret | Used for |
| ------ | -------- |
| `DOCKER_USERNAME` / `DOCKER_PASSWORD` | Docker Hub login |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Production DB credentials |
| `JWT_SECRET`, `FOOTBALL_DATA_API_TOKEN` | App runtime secrets |
| `DEPLOY_HOST`, `DEPLOY_USERNAME`, `DEPLOY_KEY` | SSH access to Digital Ocean droplet |
| `HOST_DOMAIN`, `LETSENCRYPT_EMAIL` | nginx-proxy virtual host + TLS cert |

### Production infrastructure

The droplet runs two separate Docker Compose stacks that share an external `nginx` network.

**nginx reverse proxy** (always-on, manually managed):

```yaml
nginx-proxy:
  image: nginxproxy/nginx-proxy:1.7
  ports: ["80:80", "443:443"]
  volumes:
    - /var/run/docker.sock:/tmp/docker.sock:ro   # auto-detects containers
    - certs:/etc/nginx/certs:ro

letsencrypt:
  image: nginxproxy/acme-companion:2.5
  depends_on: nginx-proxy
  # issues/renews TLS certificates automatically via ACME
```

Any container that sets `VIRTUAL_HOST` and `LETSENCRYPT_HOST` environment variables is automatically picked up by `nginx-proxy` and gets a certificate.

**Root-domain redirect** (companion stack):

`ligipredictor.com` and `www.ligipredictor.com` redirect to `https://beta.ligipredictor.com` via a minimal nginx container:

```nginx
# redirect.conf
server {
    listen 80;
    server_name _;
    return 301 https://beta.ligipredictor.com$request_uri;
}
```

```yaml
redirect:
  image: nginx:alpine
  environment:
    VIRTUAL_HOST: ligipredictor.com,www.ligipredictor.com
    LETSENCRYPT_HOST: ligipredictor.com,www.ligipredictor.com
  networks:
    - nginx   # external — the shared nginx-proxy network
  volumes:
    - ./redirect.conf:/etc/nginx/conf.d/default.conf:ro
```

The app itself runs on `beta.ligipredictor.com` and is wired into the same `nginx` network via `VIRTUAL_HOST`/`LETSENCRYPT_HOST` values injected from secrets at deploy time.

## Documentation

- [Backend development guide](docs/backend-dev.md) -- running, testing, formatting, DevTools, Thymeleaf patterns
- [API endpoints](docs/api-endpoints.md) -- REST API reference
- [Debugging tests](docs/debugging-tests.md) -- test troubleshooting
- [Prediction page UI](docs/prediction-page-ui.md) -- prediction page design and navigation logic
- [Leaderboard](docs/leaderboard.md) -- leaderboard feature docs
- [Functional error handling](docs/dev/functional-either.md) -- Either type usage

## License

See [LICENSE](LICENSE).
