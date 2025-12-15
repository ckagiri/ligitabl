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

## Further documentation

For more details on running the backend, database/migrations, seeding, and troubleshooting, see:

- Backend dev guide: `docs/backend-dev.md`
- API endpoints and examples: `docs/api-endpoints.md`
- Functional Either guide: `docs/dev/functional-either.md`
- Chat index: `docs/chat/INDEX.md`
