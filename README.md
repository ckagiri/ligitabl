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
- **Testing**: JUnit 5, Testcontainers, WireMock
- **Build**: Maven multi-module, safety-first Makefile

## Design principles

- Desktop-first, responsive UI
- API-driven match data (Football-Data.org)
- Automated round progression and finalization
- Simple structure validation (strategic freedom)
- Mid-season launch friendly
- Database-tracked "results banner" state (cross-device)
- Immediate round opening after scoring

## Requirements

- Java 21
- Maven 3.9+
- Docker + Docker Compose (recommended for local Postgres)

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
admin/        Future React-based admin UI (placeholder)
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

| Condition | Frequency |
|-----------|-----------|
| Live matches | Every 90 seconds |
| Kickoff <= 10 min | Every 1 minute |
| Kickoff <= 60 min | Every 10 minutes |
| Kickoff < 6 hours | Every 1 hour |
| No upcoming matches | Every 12 hours |
| Season complete | Every 24 hours |

When all matches in a round complete, finalization triggers automatically.

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
make import-bl            # Import Bundesliga matches
make import-sa            # Import Serie A matches
make import-pd            # Import La Liga matches
make import-fl1           # Import Ligue 1 matches

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

## Documentation

- [Backend development guide](docs/backend-dev.md) -- running, testing, formatting, DevTools, Thymeleaf patterns
- [API endpoints](docs/api-endpoints.md) -- REST API reference
- [Debugging tests](docs/debugging-tests.md) -- test troubleshooting
- [Prediction page UI](docs/prediction-page-ui.md) -- prediction page design and navigation logic
- [Leaderboard](docs/leaderboard.md) -- leaderboard feature docs
- [Functional error handling](docs/dev/functional-either.md) -- Either type usage

## License

See [LICENSE](LICENSE).
