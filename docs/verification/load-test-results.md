# Load Test Results — 2026-09-11

This answers a concrete question: **can this system support 50+ real
concurrent users?** Rather than guess from the architecture, it was
tested — a Python script (`load_test.py`, included in this folder)
fires N concurrent virtual users at the real running backend
(`mvn spring-boot:run`) against the real local MySQL instance, each one
doing what a resident actually does: register, then load the home page
(`GET /api/collections/upcoming` + `GET /api/preferences`).

Registration was chosen as the stress case on purpose — it is the most
expensive request in the app (BCrypt password hashing plus a database
write), so it represents a worst case, not a typical one. A returning
user logging in and browsing pays the same BCrypt cost as registering
but does far fewer database writes.

## Environment

Single-machine dev setup, not representative of production hardware:
MySQL 8.0 and the Spring Boot backend running on the same modest sandbox
VM used for the rest of this project's live verification (see
`verification-log.md`). No load balancer, no connection pool tuning —
Spring Boot's HikariCP default of **10 max connections** is unchanged
(`backend/src/main/resources/application.yml` sets no `hikari.*`
properties).

## Results

| Concurrent users | Success rate | Register latency (avg / p95 / max) | Home-load latency (avg / p95 / max) | Total wall-clock time |
|---:|---:|---|---|---:|
| 50  | 50/50 (100%)   | 1439 / 1574 / 1590 ms | 231 / 329 / 349 ms   | 1.8 s |
| 100 | 100/100 (100%) | 2353 / 2976 / 3010 ms | 117 / 462 / 677 ms   | 3.1 s |
| 200 | 200/200 (100%) | 4392 / 5724 / 5877 ms | 594 / 2264 / 3083 ms | 6.0 s |

**Zero failed requests at any tested level**, including 200 simultaneous
registrations — four times the "50 users" bar in question.

## What this does and doesn't show

- It shows the app doesn't fall over, error out, or corrupt data under
  concurrent load at these levels — RBAC, JWT issuing, MyBatis writes,
  and the HikariCP pool all held up correctly under real concurrency
  (not just sequential correctness, which is all the integration test
  suite checks).
- It does **not** show production-grade throughput. Latency climbs
  noticeably as concurrency rises — almost entirely BCrypt's cost (by
  design: BCrypt is deliberately slow to resist brute-forcing) compounding
  with only 10 database connections available. 50 people registering in
  the exact same second is also an unrealistic worst case; ordinary
  traffic (some logins, mostly reads) would look much better than the
  register numbers above.
- It was run on a single unscaled dev VM with no connection pool tuning,
  no caching, and no load balancer — not representative of what a tuned
  production deployment would sustain.

## Bottom line

For "50 residents using this at once" as a realistic usage pattern (a
mix of returning-user logins and page loads, not all of them registering
in the same instant), **yes, based on this test the current
architecture handles it without falling over**. If this needed to scale
well past that, the first, well-understood levers are: raise
`spring.datasource.hikari.maximum-pool-size` beyond the default of 10,
and revisit BCrypt's work factor if login latency became the bottleneck
in practice — neither is a redesign, both are configuration.

## Reproducing this

```bash
cd docs/verification
python3 load_test.py 50    # or 100, 200, etc.
```

Requires `requests` (`pip install requests`) and a running backend at
`http://localhost:8080`. The script registers real accounts with random
emails — clean them up afterward:

```sql
DELETE FROM user_preference WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE 'loadtest-%');
DELETE FROM app_user WHERE email LIKE 'loadtest-%';
```
