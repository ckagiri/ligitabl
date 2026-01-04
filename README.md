# LigiTabl

A football league prediction game where players predict league standings each round and earn points based on
accuracy. Players can adjust predictions periodically via team swaps. Scores are calculated when rounds are
finalized by comparing predictions to computed league standings.

## Design principles

- Desktop-first, responsive UI - for demo go to https://ligipredictor.com/
- API-driven match data (Football-Data.org)
- Automated round progression and finalization
- Simple structure validation (strategic freedom)
- Mid-season launch friendly
- Database-tracked “results banner” state (cross-device)
- Immediate round opening after scoring

## Requirements

- Java 21
- Maven 3.9+
- Docker + Docker Compose (recommended for local Postgres)

## Quick start (local)

The repo uses a safety-first Makefile with explicit environments.

```bash
make env-check
make run-api

# in another terminal
curl http://localhost:8080/api/status
```

## Repo layout

- `api/`: Spring Boot REST API (controllers, use-cases, web/security, integration tests)
- `model/`: database model module (Liquibase changelogs + jOOQ-generated types)
- `seed/`: seeding CLI used to load reference/demo data into a database
- `jooq-codegen/`: helper module used to run jOOQ code generation against the current schema
- `scripts/`: end-to-end and smoke scripts (auth checks, seeding checks, demo seeding flows)
- `docs/`: developer docs (how to run, endpoints, debugging, design notes)

## Documentation

- Backend development guide: `docs/backend-dev.md`
- API reference: `docs/api-endpoints.md`
- Debugging tests: `docs/debugging-tests.md`
- Functional error handling: `docs/dev/functional-either.md`
