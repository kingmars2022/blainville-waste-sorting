# Deploying this, for nothing

A checklist for putting the application somewhere a stranger can open it,
without a credit card and without a bill.

**Nothing here has been run.** The code changes it depends on have been made
and tested — the allowed CORS origins are configurable, the Lambda jar loads
from a bare classpath, the calendar extends itself — but the deployment
itself has not been performed. Where a step is a verified fact about this
codebase it says so; where it is a fact about a third party, check it,
because free tiers change and this was written in September 2026.

## First: you need far less than the architecture diagram suggests

The application is built so that everything past MySQL is optional at
runtime. For a working public demo:

| Service | Needed? | What happens without it |
|---|---|---|
| **MySQL** | **Required** | Nothing starts. Flyway runs on boot. |
| **Redis** | Strongly recommended | Caching falls through to MySQL; the admin agent's plan hand-off stops working. |
| Kafka | No | `EVENTS_ENABLED=false`. The outbox still records into MySQL, so no event is lost — the relay just does not run. |
| MongoDB | No | `AUDIT_STORE=mysql` (the default) keeps the audit trail in MySQL. The gap report is the only thing that goes dark. |
| S3 + Lambda | No | The photo feature returns errors. Everything else is unaffected. |
| Anthropic key | No | `ASSISTANT_PROVIDER=template` (the default) answers from the guide with no model and no cost. |

So the minimum is **a MySQL, a Redis, somewhere to run one container, and
somewhere to serve static files.** The photo feature is the one thing that
genuinely cannot be free — it needs a real AWS account with a card on file —
so leave it off and say so on the page.

## The services, and what they cost

Checked September 2026. Verify before relying on any of it.

| Piece | Service | Free terms | Card? |
|---|---|---|---|
| MySQL | [Aiven for MySQL](https://aiven.io/free-mysql-database) | 1 GB RAM, 1 GB storage, one free service per type, no time limit | **No** |
| Redis | [Upstash](https://upstash.com/pricing/redis) | 256 MB, 500k commands/month, 10 GB bandwidth | **No** |
| Backend container | [Koyeb](https://www.koyeb.com/) | One service, 512 MB RAM, 0.1 vCPU, does not sleep | Usually no — may ask if it cannot verify you are human |
| Backend (alternative) | [Render](https://render.com/) | Free web service, **sleeps when idle** (cold start on first hit) | No |
| Frontend (static) | Cloudflare Pages / Netlify / GitHub Pages | Generous static hosting | No |

**Fly.io is no longer an option.** Its free Hobby allowance was withdrawn;
new accounts get a trial of 2 VM-hours or 7 days and require a card. It is
still in a lot of older tutorials.

Two consequences worth deciding up front:

- **Aiven powers off a free service that sits idle**, with a warning email
  first. For a portfolio link that gets opened once a week, expect to wake it
  up before an interview.
- **Render's free tier sleeps.** First request after idle takes tens of
  seconds — on a JVM, long enough that an interviewer assumes it is broken.
  Koyeb not sleeping is why it is listed first, despite the smaller box.

## MySQL, not Postgres

Not interchangeable here, so do not take the Postgres free tier that every
list recommends. The schema uses MySQL-specific features:

- `FULLTEXT` indexes with **two parsers** — the default word parser for
  French and English, and `ngram` for Chinese. Postgres has no ngram
  full-text parser.
- `on duplicate key update`, which the calendar top-up relies on to be
  repeatable.
- Flyway migrations written in MySQL dialect, including a `STORED GENERATED`
  column.

## Steps

### 1. MySQL

Create the free MySQL service. Take its host, port, database name, user and
password.

The app builds its own JDBC URL from `DB_URL`, so use the whole string:

```
DB_URL=jdbc:mysql://HOST:PORT/DATABASE?useUnicode=true&characterEncoding=utf8&serverTimezone=America/Toronto&sslMode=REQUIRED
```

Managed MySQL normally requires TLS — `sslMode=REQUIRED` is the MySQL
Connector/J parameter for that.

Nothing else to do: **Flyway creates every table and seeds the data on first
boot** (13 migrations, verified to apply cleanly to an empty database).

### 2. Redis

Create the free Redis. Upstash speaks the real Redis protocol, and this app
needs two things from it: `EVAL` (the rate limiter's atomic counter) and
`GETDEL` (single-use agent plans, Redis 6.2+). Confirm both are available on
whatever you pick.

Upstash requires TLS and a password. **No code change is needed** — Spring
Boot reads any `spring.*` property from the environment, which was verified
by setting it and watching the behaviour change:

```
REDIS_HOST=<host>
REDIS_PORT=<port>
SPRING_DATA_REDIS_PASSWORD=<password>
SPRING_DATA_REDIS_SSL_ENABLED=true
```

One caution: the Redis timeouts are set to **500 ms** deliberately, so a dead
Redis costs milliseconds rather than seconds. Across the public internet to a
different provider, that is tight. If you see cache misses and warnings about
Redis under load, raise it:

```
SPRING_DATA_REDIS_TIMEOUT=2000ms
SPRING_DATA_REDIS_CONNECT_TIMEOUT=2000ms
```

Getting Redis settings wrong shows up loudly at startup rather than quietly —
observed while testing the TLS flag.

### 3. Backend container

Point the platform at this repository with `backend/Dockerfile`. It is a
two-stage build: Maven builds the jar, then a JRE image runs it. No build
command to configure.

**Port.** The app listens on 8080 and does not read `PORT`. Platforms differ:

- **Koyeb / anything with a configurable port** — set the service port to
  8080 and change nothing.
- **Render and anything that injects `PORT`** — set the environment variable
  `SERVER_PORT` to whatever they inject, or add one line to
  `backend/src/main/resources/application.yml`:

  ```yaml
  server:
    port: ${PORT:8080}
  ```

**Memory.** A 512 MB box is tight for a Spring Boot JVM. Set:

```
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
```

**Health check.** There is no actuator in this project. Use
`GET /api/sorting-items` — it is public, returns 200, and touches MySQL, so
it is a real readiness check rather than a liveness fiction.

Environment variables for the service:

```
DB_URL=jdbc:mysql://...            # from step 1
DB_USERNAME=...
DB_PASSWORD=...
REDIS_HOST=...                     # from step 2
REDIS_PORT=...
SPRING_DATA_REDIS_PASSWORD=...
SPRING_DATA_REDIS_SSL_ENABLED=true
CORS_ALLOWED_ORIGINS=https://your-frontend-domain
APP_JWT_SECRET=<a long random string, not the default>
APP_ADMIN_EMAIL=<your admin email>
APP_ADMIN_PASSWORD=<a real password, not the default>
EVENTS_ENABLED=false               # no Kafka
AUDIT_STORE=mysql                  # no MongoDB
ASSISTANT_PROVIDER=template        # no API key, no cost
```

`APP_JWT_SECRET` and `APP_ADMIN_PASSWORD` have development defaults in
`application.yml`. Deploying without overriding both means shipping a public
admin account with a published password.

### 4. Frontend

```bash
cd frontend && npm ci && npm run build      # produces dist/
```

Publish `dist/` to Cloudflare Pages, Netlify or GitHub Pages.

The dev server proxies `/api` to `localhost:8080`; in production nothing does
that, so the built site must reach the backend by its real URL. The API
client calls relative paths (`/api/...`), so either:

- **Put both behind one domain** — a reverse proxy or the host's rewrite
  rules mapping `/api/*` to the backend. Simplest, and it keeps the browser
  same-origin, so CORS barely matters.
- **Or serve them from different domains** — then `CORS_ALLOWED_ORIGINS` must
  name the frontend's exact origin, and the API client needs a base URL
  rather than a relative path. That is a small code change this repository
  has not made.

The first option is less work and fewer moving parts.

### 5. Check it

```bash
curl https://your-backend/api/sorting-items            # 200, a JSON array
curl -i -X OPTIONS https://your-backend/api/sorting-items \
     -H "Origin: https://your-frontend-domain" \
     -H "Access-Control-Request-Method: GET"           # 200 + Access-Control-Allow-Origin
```

Then open the site, switch the language, and sign in with the admin account.

## What to say about it

Be straight about what is running. Something like:

> Live demo on free-tier infrastructure. The photo feature is disabled
> because it needs a paid AWS account; events and the document store are
> switched off, and the application degrades to MySQL for both by design.

That reads better than a demo where a third of the buttons return errors with
no explanation.

## Costs, plainly

Everything above is $0 with no card, with these exceptions:

- **Photo uploads** need AWS (S3, Lambda, API Gateway). The free tier covers
  this workload comfortably, but AWS requires a card on file and will bill you
  if you exceed it. Left off by default.
- **Claude-worded answers** need an Anthropic API key, which is paid per
  request. The default `template` provider costs nothing and works on a fresh
  clone.
- Koyeb may ask for a card for human verification, which is not the same as
  being charged — but decide whether you mind before starting.
