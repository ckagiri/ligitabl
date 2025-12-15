# API Endpoints

This document lists the main HTTP endpoints exposed by the API, plus example requests and error responses.

## Status

- `GET /api/status` — simple service health/status.

## Teams

- `GET /api/teams` — list all teams
- `GET /api/teams?id={uuid}` — get a team by UUID (query parameter)
- `GET /api/teams/{slug}` — get a team by slug (path parameter)
- `POST /api/teams` — create a team
  - Body: JSON with fields `name`, `shortName`, `slug`, `tla`
  - Response: `201 Created` with `Location: /api/teams/{id}` and the created team payload
- `PUT /api/teams/{id}` — update a team by UUID
  - Body: JSON with fields `name`, `shortName`, `slug`, `tla`
  - Response: `200 OK` with the updated team payload
- `DELETE /api/teams/{id}` — delete a team by UUID
  - Response: `204 No Content`

Notes:

- Getting by ID uses a query parameter (`id`) to avoid ambiguity with slug in the path.
- Team payload shape (request/response):
  - `name`: string (required)
  - `shortName`: string (required)
  - `slug`: string (required, lowercase letters/digits/hyphens)
  - `tla`: string (required, exactly 3 characters)

### Examples (curl)

```bash
# List teams
curl -s http://localhost:8080/api/teams | jq .

# Get by ID (query param)
curl -s "http://localhost:8080/api/teams?id=22b2c3d4-aaaa-bbbb-cccc-1234567890ab" | jq .

# Get by slug
curl -s http://localhost:8080/api/teams/arsenal | jq .

# Create
curl -s -X POST http://localhost:8080/api/teams \
   -H 'Content-Type: application/json' \
   -d '{
      "name":"Arsenal Football Club",
      "shortName":"Arsenal",
      "slug":"arsenal",
      "tla":"ARS"
   }' | jq .

# Update
curl -s -X PUT http://localhost:8080/api/teams/22b2c3d4-aaaa-bbbb-cccc-1234567890ab \
   -H 'Content-Type: application/json' \
   -d '{
      "name":"Arsenal FC",
      "shortName":"Arsenal",
      "slug":"arsenal",
      "tla":"ARS"
   }' | jq .

# Delete
curl -i -X DELETE http://localhost:8080/api/teams/22b2c3d4-aaaa-bbbb-cccc-1234567890ab
```

### Error responses

Error responses follow a consistent shape and HTTP status code, for example:

```json
{
  "message": "Team not found: arsenal",
  "error": "Not Found",
  "status": 404,
  "path": "uri=/api/teams/arsenal",
  "timestamp": "2025-11-03T12:34:56.789"
}
```
