# GhostLap API (v1)

Two services, both JSON over HTTP:

| Service | Base URL (local) | Swagger UI |
|---|---|---|
| player-service | `http://localhost:8081` | http://localhost:8081/swagger-ui.html |
| race-service | `http://localhost:8082` | http://localhost:8082/swagger-ui.html |

Start everything with `cd infra` then `docker compose up --build` (copy `infra/.env.example` to `infra/.env` first).

## Authentication

1. Call `register` or `login` on player-service. The response contains a `token` (a JWT).
2. Send it on every protected call: `Authorization: Bearer <token>`.
3. Tokens are valid for 24 hours. Both services check the same signature (HS256, shared `JWT_SECRET`).

The token's claims: `iss` = `ghostlap`, `sub` = player id, `username`, `iat`, `exp`.

| Endpoint | Token needed |
|---|---|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | no |
| `GET /api/v1/players/me` | yes |
| `POST /api/v1/laps` | yes |
| `GET /api/v1/tracks/{trackId}/leaderboard`, `.../ghosts/top`, `GET /api/v1/laps/{lapId}/ghost` | no (public) |
| `/actuator/health`, `/actuator/prometheus`, `/swagger-ui.html`, `/v3/api-docs` | no |

## Errors

Every error is an RFC 7807 **ProblemDetail** with content type `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Username 'reet' is already taken",
  "instance": "/api/v1/auth/register"
}
```

Validation errors (400) add an `errors` object, one message per field:

```json
{ "status": 400, "detail": "Request validation failed",
  "errors": { "password": "size must be between 8 and 72", "username": "may only contain letters, digits and _" } }
```

| Status | Meaning |
|---|---|
| 400 | Request body or query parameter is malformed |
| 401 | Missing, invalid or expired token; or wrong username/password |
| 404 | Unknown track, lap, ghost or player |
| 409 | Username already taken |

---

## player-service

### `POST /api/v1/auth/register`
Create an account. Passwords are stored as BCrypt hashes.

Request:
```json
{ "username": "reet", "password": "password123" }
```
- `username`: 3 to 20 characters, letters, digits and `_`. Unique, ignoring case.
- `password`: 8 to 72 characters (BCrypt only uses the first 72 bytes).

Response `201 Created`:
```json
{ "playerId": 1, "token": "eyJhbGciOiJIUzI1NiJ9..." }
```
Errors: `400` invalid fields, `409` username taken.

### `POST /api/v1/auth/login`
Same request body as register. Response `200 OK` with `{playerId, token}`.
Errors: `400` invalid fields, `401` "Invalid username or password" (same message whether the user exists or not).

### `GET /api/v1/players/me`  (token)
Response `200 OK`:
```json
{ "playerId": 1, "username": "reet", "createdAt": "2026-10-29T20:48:18.776324Z" }
```
Errors: `401` no/bad token, `404` the player was deleted.

---

## race-service

### `POST /api/v1/laps`  (token)
Submit a finished lap with its ghost. The server validates it (anti-cheat) and always stores it.

Request:
```json
{
  "trackId": 1,
  "lapTimeMs": 16000,
  "checkpointTimesMs": [2000, 4000, 6000, 8000, 10000, 12000, 14000, 16000],
  "ghost": "H4sIAAAAAAAC/..."
}
```
- `checkpointTimesMs`: time since the lap started (ms) at each of the 8 checkpoints, in order.
  Checkpoint 0 is the start/finish line, so it is crossed last and its time equals `lapTimeMs`.
- `ghost`: the GhostCodec file (gzipped GHST format, see CLAUDE.md), base64-encoded. At most 400,000 characters.

A ready-made valid body is in [examples/lap-16s.json](examples/lap-16s.json).

Response `201 Created`, accepted:
```json
{ "lapId": 1, "status": "ACCEPTED", "rank": 1 }
```
`rank` is the player's position on the track leaderboard after this lap (their best lap counts, 1 = fastest).

Response `201 Created`, rejected (stored, never on the leaderboard, ghost not kept):
```json
{ "lapId": 2, "status": "REJECTED", "reason": "TOO_FAST" }
```

Rejection reasons, checked in this order (the first failure is returned):

| Reason | Rejected when |
|---|---|
| `TOO_FAST` | `lapTimeMs` is below the track's minimum plausible time (track 1: 13,000 ms) |
| `CHECKPOINT_ORDER` | not exactly 8 checkpoint times, not strictly increasing from 0, or the last one ≠ `lapTimeMs` |
| `BAD_GHOST` | not base64, not gzip, not a valid GHST file, or its header doesn't match the lap (track id, lap time, 30 Hz rate) |
| `SAMPLE_COUNT_MISMATCH` | sample count differs from `lapTimeMs × 30 / 1000` by more than 2 |
| `SPEED_EXCEEDED` | distance between two consecutive samples × 30 is above `MAX_SPEED × 1.1` (660 units/s) |

Errors: `400` malformed body, `401` no/bad token, `404` unknown track.

### `GET /api/v1/tracks/{trackId}/leaderboard?limit=10`
Fastest players on a track, one row per player (their best accepted lap). `limit` is 1 to 100, default 10.

Response `200 OK`:
```json
[
  { "rank": 1, "username": "fast", "lapTimeMs": 14000, "lapId": 7 },
  { "rank": 2, "username": "reet", "lapTimeMs": 16000, "lapId": 1 }
]
```
Errors: `400` limit out of range, `404` unknown track.

### `GET /api/v1/tracks/{trackId}/ghosts/top`
The ghost of the fastest accepted lap on the track.

Response `200 OK`:
```json
{
  "lapId": 7,
  "username": "fast",
  "trackId": 1,
  "lapTimeMs": 14000,
  "submittedAt": "2026-10-29T20:48:19.850229Z",
  "ghost": "H4sIAAAAAAAC/..."
}
```
Decode `ghost` with base64, then `GhostCodec.decode`. Errors: `404` unknown track or no accepted laps yet.

### `GET /api/v1/laps/{lapId}/ghost`
That lap's ghost, same response shape as above. Errors: `404` unknown lap, or the lap was rejected.

---

## Try it in Swagger UI

1. http://localhost:8081/swagger-ui.html → **Auth → POST /register** → *Try it out* → `{"username": "reet", "password": "password123"}` → *Execute*. Copy the `token`.
2. http://localhost:8082/swagger-ui.html → **Authorize** (top right) → paste the token → *Authorize*.
3. **Laps → POST /api/v1/laps** → *Try it out* → paste the contents of [examples/lap-16s.json](examples/lap-16s.json) → *Execute*. Expect `"status": "ACCEPTED", "rank": 1`.
4. **Leaderboards → GET /leaderboard** with `trackId = 1` → your lap is there.
5. To see a rejection, submit the example again with `"lapTimeMs": 9000` → `"status": "REJECTED", "reason": "TOO_FAST"`
   (TOO_FAST is checked first). Changing one character in the middle of `ghost` gives `BAD_GHOST`.

## Ops endpoints (both services)
- `GET /actuator/health`: `{"status": "UP"}` when the database (and Redis, for race-service) are reachable.
- `GET /actuator/prometheus`: metrics in Prometheus text format.
- `GET /v3/api-docs`: the OpenAPI document that Swagger UI renders.
