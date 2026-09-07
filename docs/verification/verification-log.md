# Live Verification Log — 2026-09-07

This records a manual end-to-end verification session run against a real
local MySQL instance and the actual Spring Boot / Vite dev servers (not
mocks), following up on the unit-test-only coverage that existed before.
It exists to document what was actually exercised, what broke, and what
was fixed — not as a substitute for the automated test suite
(`mvn test` and `mvn test -Pintegration-test`, see the main README).

Log excerpts below are lightly cleaned (proxy/classpath noise removed,
credentials redacted) but otherwise reflect real command output from the
session, not reconstructed examples.

## Environment

- MySQL 8.0 (installed locally, not via Docker — Docker was unavailable
  in the sandbox this session ran in)
- Backend: `mvn spring-boot:run` against `bienvenue_blainville`
- Frontend: `npm run dev` (Vite, default port 5173)
- Screenshots taken with a headless Chromium via Playwright, driving the
  real running frontend against the real running backend

## Bugs found and fixed

None of these were caught by the pre-existing Mockito-based unit tests,
since those never construct a real Spring context, a real security
filter chain, or a real database connection. All are now covered by the
integration test suite added afterward
(`AuthenticationFlowIntegrationTest`, `mvn test -Pintegration-test`).

1. **`SecurityConfig` crashed the app at startup.** The `AuthenticationManager`
   bean was built from the shared `AuthenticationManagerBuilder`, which
   conflicted with Spring Security's own internal use of that same builder
   (`IllegalStateException: Cannot apply ... to already built object`).
   Fixed by exposing a plain `AuthenticationProvider` bean wrapped in a
   `ProviderManager` instead.
2. **`AppUserMapper.insert` crashed `AdminSeeder` at startup.** It used
   `useGeneratedKeys`/`keyProperty="id"` with multiple `@Param` scalar
   arguments; MyBatis could not determine which parameter to assign the
   generated key to. The key was never actually read by any caller, so
   the clause was simply removed.
3. **Every admin "create" endpoint threw `ClassCastException`.**
   `(Long) params.get("id")` failed because MySQL's JDBC driver returns
   `BIGINT` generated keys as `BigInteger`, not `Long`. Spring's filter
   chain surfaced this as a bare `403`, which initially looked like an
   authorization bug rather than the real cause. Fixed with
   `((Number) params.get("id")).longValue()` in `CollectionService`,
   `SortingItemService`, and `SpecialNoticeService`.
4. **Missing/invalid/expired tokens returned `403` instead of `401`.**
   Spring Security's default handling collapses "not authenticated" and
   "authenticated but forbidden" into 403 without an explicit
   `AuthenticationEntryPoint`. Fixed by adding one (401) plus an explicit
   `AccessDeniedHandler` (403). The first attempt at this fix broke the
   403 case: `sendError(403)` triggers Tomcat's internal forward to
   `/error`, which re-enters the whole security filter chain as a fresh
   anonymous request and got reauthenticated into a 401, overwriting the
   original status. Root-caused with Spring Security `DEBUG` logging
   rather than guessing; fixed by adding `/error` to `permitAll()`.
5. **`SpecialNoticeService` accepted `endsOn` before `startsOn`.** Added
   a same-service validation check (400 Bad Request) on create and update.

## Manually verified (curl), sanitized transcripts

Auth and validation:

```text
POST /api/auth/register (duplicate email)          -> 409 "An account already exists for this email."
POST /api/auth/register (malformed email)           -> 400 {"email":"must be a well-formed email address"}
POST /api/auth/register (password too short)        -> 400 {"password":"size must be between 8 and 100"}
POST /api/auth/login    (wrong password)             -> 401 "Invalid email or password."
POST /api/auth/login    (unknown email)               -> 401 "Invalid email or password."   (same message — does not leak account existence)
GET  /api/preferences   (no Authorization header)    -> 401
GET  /api/preferences   (garbage token)               -> 401
GET  /api/preferences   (valid resident token)        -> 200
```

RBAC, across all three admin resource types:

```text
POST   /api/admin/collections     (resident token) -> 403
POST   /api/admin/sorting-items   (resident token) -> 403
DELETE /api/admin/notices/{id}    (resident token) -> 403
GET    /api/admin/notices         (admin token)    -> 200
```

Admin CRUD, including update and not-found handling:

```text
POST /api/admin/collections            -> 201 {"id":22,...}
PUT  /api/admin/collections/{id}       -> 200 (sector/type/bin all changed as requested)
DELETE /api/admin/collections/999999   -> 404 "Collection event 999999 not found"

POST /api/admin/sorting-items          -> 201 (fr/en/zh translations + per-language keywords all persisted)
PUT  /api/admin/sorting-items/{id}     -> 200 (translations replaced correctly)

POST /api/admin/notices (endsOn before startsOn) -> 400 "endsOn must not be before startsOn."
```

SQL injection safety (MyBatis parameterized queries):

```text
POST /api/admin/notices {"titleFr": "Robert'); DROP TABLE special_notice;--", ...} -> 201
-- the payload was stored as a literal string; the table was untouched
```

## Screenshots

Captured from the real running frontend (`http://localhost:5173`) against
the real running backend, using a seeded/test dataset — see
`screenshots/` in this folder:

- `01-home.png` — home page, live data from `GET /api/collections/upcoming`
- `02-sorting.png` — sorting guide page
- `03-login.png` — login form
- `04-home-logged-in.png` — home page after signing in through the real UI login flow
- `05-admin.png` — admin dashboard: real collection schedule (20 rows),
  sorting items (15 rows), and the notices panel, all backed by the live API

## Test suite result at the time of this session

```text
mvn test                    14/14 passing (unit, mocked, no DB required)
mvn test -Pintegration-test 21/21 passing (14 unit + 7 integration against real MySQL)
```
