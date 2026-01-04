# API Endpoints

This document lists the HTTP endpoints exposed by the API. It is derived from the `@RestController` mappings under `api/src/main/java`.

Base URL (local dev): `http://localhost:8080`

## Public endpoints

### Status

- `GET /api/status` — lightweight status payload (`status`, `service`, `timestamp`)

### Auth

- `POST /auth/login` — login (returns access token)
- `POST /auth/register` — register a user (returns `{ publicId, email, displayName, roles }`)

## Authenticated endpoints

These endpoints require an `Authorization: Bearer <token>` header.

### Access / identity

- `GET /api/me` — current user info
- `GET /api/admin` — admin-only
- `GET /api/player` — player-only

### Teams

- `GET /api/teams` — list all teams
- `GET /api/teams?id={uuid}` — get a team by UUID (query parameter)
- `GET /api/teams/{slug}` — get a team by slug (path parameter)
- `POST /api/teams` — create a team
- `PUT /api/teams/{id}` — update a team by UUID
- `DELETE /api/teams/{id}` — delete a team by UUID

Note: Getting by ID uses a query parameter (`id`) to avoid ambiguity with slug in the path.

### Competitions / seasons / rounds

- `GET /api/competitions` — list competitions
- `GET /api/competitions/{slug}` — get competition by slug

- `GET /api/competitions/{competitionSlug}/seasons` — list seasons for a competition
- `GET /api/competitions/{competitionSlug}/seasons/{seasonSlug}` — get season by slug

- `GET /api/competitions/{competitionSlug}/seasons/{seasonSlug}/rounds` — list rounds in a season
- `GET /api/competitions/{competitionSlug}/seasons/{seasonSlug}/rounds/{position}` — get round by position

### Matches

- `GET /api/competitions/{competitionSlug}/seasons/{seasonSlug}/rounds/{position}/matches` — matches for a given round
- `GET /api/rounds/default/matches` — matches for the default competition’s current round

### Contest

- `GET /api/contest/status` — contest status for current user
- `POST /api/contest/join` — join the current season contest

### Predictions

- `POST /api/predictions/swap` — make a swap in current prediction

### Leaderboard

- `GET /api/contests/main/leaderboard?phase=Q2` — leaderboard for default competition’s main contest
  - `phase` is optional; defaults to full season

## Admin endpoints

- `POST /api/admin/rounds/default/finalize` — finalize the default competition’s current round (admin-only)

## Example (curl)

```bash
# 1) Login
token=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}' | jq -r .accessToken)

# 2) Call an authenticated endpoint
curl -s http://localhost:8080/api/me -H "Authorization: Bearer $token" | jq .
```
