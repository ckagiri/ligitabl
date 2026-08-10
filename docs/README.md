# Project Docs

- Backend dev guide: [Backend Development Guide](./backend-dev.md)
- Frontend dev guide: [Frontend Development Guide](./frontend-dev.md)
- API reference: [API Endpoints](./api-endpoints.md)
- Prediction page: [Prediction Page UI States & Banners](./prediction-page-ui.md)
- Final Table: [Share card and saved state](./final-table-share.md)
- Leaderboard: [Leaderboard Persistence](./leaderboard.md)
- Debugging guide: [Debugging Test Failures](./debugging-tests.md)
- Error handling: [Functional Either](./dev/functional-either.md)

## Common commands

- Core API tests (skip `*IT`): `make test-api-core`
- API integration tests (`*IT`): `make test-api-it`
- Full API suite: `make test-api-all`
- Start API (fast): `make run-api-fast`
- Reset dev DB + seed: `make dev-reset`
- Format code: `make format`

## Environment files (`.env`)

The root `Makefile` is safety-first and uses explicit environments.

- Default is `ENV=test` (uses `.env.test`).
- Use `ENV=dev` (uses `.env.dev`) for daily development.
- Use `ENV=prod` (uses `.env.prod`) only with explicit confirmation.

Optional per-environment local overrides (gitignored): `.env.test.local`, `.env.dev.local`, `.env.prod.local`.

Handy commands:

- `make env-check`
- `make env-info` (or `make env-info ENV=dev`)
