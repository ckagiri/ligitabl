# Project Docs

- Backend dev guide: [Backend Development Guide](./backend-dev.md)
- API reference: [API Endpoints](./api-endpoints.md)
- Debugging guide: [Debugging Test Failures](./debugging-tests.md)
- Guide: [Functional Either](./dev/functional-either.md)

## Common commands

- Core API tests (skip `*IT`): `make test-api-core`
- API integration tests (`*IT`): `make test-api-it`
- Full API suite: `make test-api-all`

## Environment files (`.env`)

The root `Makefile` supports environment layering for local dev:

- Loads `.env` if present.
- Also loads `.env.local` if present (recommended for secrets and machine-specific overrides).

Tip: for a “test” environment, you can export variables from a separate file in your shell before running Make targets.
