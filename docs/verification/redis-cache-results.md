# Redis Cache — Measurements — 2026-09-19

Adding Redis to a project is easy. Showing what it bought, and what it cost
when it broke, is the part worth writing down. Everything below is measured
against the real running backend, real MySQL, and real `redis-server 7.0.15`
on the same sandbox VM used for the rest of this project's verification
(see `verification-log.md` and `load-test-results.md`).

## What is cached, and why only this

Two reads, both on the critical path of every home page load:

| Cache | Method | Key | TTL |
|---|---|---|---|
| `collections:upcoming` | `CollectionService.upcoming` | `<today>:<sector>:<days>` | 10 min |
| `notices:active` | `SpecialNoticeService.active` | `<today>` | 5 min |

Nothing else is cached. Admin list endpoints are low-traffic, the sorting
guide search is user-input-driven (an unbounded key space), and auth must not
be cached at all. A cache that covers everything is a cache nobody can reason
about.

**The day is part of both keys on purpose.** Both methods answer "as of
today" — `upcoming` reads `LocalDate.now(clock)`, `active` filters on
`starts_on`/`ends_on`. A key of just `(sector, days)` would serve yesterday's
"next collection" after midnight, which is the single moment this page has to
be right.

## What it bought

300 concurrent home-page loads (each load = `GET /api/collections/upcoming`
+ `GET /api/notices/active`, i.e. 600 reads), via
[`cache_benchmark.py`](cache_benchmark.py):

| | `CACHE_TYPE=none` | `CACHE_TYPE=redis` | Change |
|---|---:|---:|---|
| MySQL `SELECT`s executed | 603 | **1** | −99.8% |
| Latency avg | 338 ms | **73 ms** | −78% |
| Latency p50 | 338 ms | 64 ms | −81% |
| Latency p95 | 577 ms | 153 ms | −73% |
| Latency max | 703 ms | 254 ms | −64% |
| Successful | 300/300 | 300/300 | — |

`Com_select` was read from MySQL's own `SHOW GLOBAL STATUS` immediately
before and after each run, so "603 → 1" is the database's count, not the
application's. Redis reported `keyspace_hits:1403 / keyspace_misses:4`
across the session — a 99.7% hit rate, which is what you would expect for
data that changes when an administrator edits a schedule, not when a user
does anything.

**Honest caveat on throughput:** wall-clock throughput was ~330–400 page
loads/s in *both* configurations. At this concurrency the Python test
harness is the bottleneck, not the server, so these runs measure per-request
latency and database offload — they do not establish a throughput ceiling.
The database-offload number is the one that would matter in production: it
is what lets the same MySQL instance serve far more residents.

## What it cost when Redis died — and the bug that found

The cache must never be able to take the site down. It doesn't — but the
first implementation made the site unusable anyway, and only actually
killing `redis-server` under a running application exposed it.

With the `CacheErrorHandler` already swallowing every Redis failure:

```text
redis-cli SHUTDOWN NOSAVE
GET /api/collections/upcoming?sector=north&days=30 -> HTTP 200 in 4.018s
GET /api/collections/upcoming?sector=north&days=30 -> HTTP 200 in 4.014s
GET /api/notices/active                            -> HTTP 200 in 4.012s
```

Correct data, HTTP 200, zero 5xx — and four seconds per request. The logs
explained it: `RedisCommandTimeoutException: Command timed out after 2
second(s)`, twice per request. Lettuce's default `DisconnectedBehavior`
**queues** commands while the connection is down instead of failing them, so
each request paid the 2 s command timeout for the failed cache read and
again for the failed cache write.

Fixed in `CacheConfig` by rejecting commands outright while disconnected:

```java
ClientOptions.builder()
        .autoReconnect(true)
        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
        ...
```

Same test, after:

```text
redis-cli SHUTDOWN NOSAVE
GET /api/collections/upcoming?sector=north&days=30 -> HTTP 200 in 0.018s
GET /api/collections/upcoming?sector=north&days=30 -> HTTP 200 in 0.010s
GET /api/collections/upcoming?sector=north&days=30 -> HTTP 200 in 0.010s
GET /api/notices/active                            -> HTTP 200 in 0.007s
```

**4.0 s → 0.01 s.** Payloads still correct (7 events), zero 5xx, and the log
carries `Cache read failed (...), falling through to the database` instead of
a stack trace reaching the user.

Recovery needs no restart. Starting `redis-server` again and re-issuing the
same request repopulated the key and the following request was a cache hit
once more — `autoReconnect` handles it:

```text
redis-server --daemonize yes
GET .../upcoming -> HTTP 200 in 0.011s   # repopulates
redis-cli KEYS '*' -> collections:upcoming::2026-09-19:north:30
GET .../upcoming -> HTTP 200 in 0.008s   # cache hit again
```

## Eviction, verified live

An admin write must not wait out the TTL — a cancelled collection is exactly
the notice residents need immediately:

```text
redis-cli KEYS 'collections:upcoming*'  -> collections:upcoming::2026-09-19:north:30
POST /api/admin/collections             -> 201 {"id":24,"collectionDate":"2026-09-21",...}
redis-cli KEYS 'collections:upcoming*'  -> (empty)          # evicted by @CacheEvict
GET  /api/collections/upcoming?...      -> now includes 2026-09-21
redis-cli KEYS 'collections:upcoming*'  -> repopulated
```

`allEntries = true` rather than a single key: one schedule edit can change
the answer for several sectors and every `days` horizon at once, so there is
no narrower key worth computing.

## What is stored

Plain JSON of a declared type — not Spring's default polymorphic format:

```text
redis-cli GET 'collections:upcoming::2026-09-19:north:30'
[{"id":6,"collectionDate":"2026-09-24","sector":"all","collectionType":"organic",
  "binColor":"brown","noteFr":"Les matieres organiques sont collectees le jeudi...",
  "noteZh":"厨余和有机垃圾全市每周四收集。","sourceUrl":"https://blainville.ca/..."}]
```

`GenericJackson2JsonRedisSerializer` writes `@class` type hints that do not
round-trip reliably for Java `record`s — records are `final`, so Jackson's
default typing skips them and the list elements come back as
`LinkedHashMap`. Declaring the exact `List<CollectionEvent>` type per cache
sidesteps that entirely and keeps the stored value legible in `redis-cli`,
which is what makes debugging a stale entry a 10-second job.

## Tests

`RedisCacheIntegrationTest` (5 tests, `mvn test -Pintegration-test`) pins all
of this down against a real Redis and a real MySQL. It detects cache hits
without mocks: it changes the database *behind* the service with a plain
`JdbcTemplate` and asserts the service still returns the old answer — only a
real cache hit produces that. A test that called the method twice and
compared results would pass whether or not anything was cached.

That the tests are not vacuous was itself checked: flipping the suite to
`CACHE_TYPE=none` fails all 5.

```text
mvn test                    14/14 passing (unit, no infrastructure)
mvn test -Pintegration-test 26/26 passing (14 unit + 12 integration, real MySQL + Redis)
```

## Reproducing this

```bash
docker compose up -d mysql redis
cd backend && mvn spring-boot:run          # cache on
cd docs/verification && python3 cache_benchmark.py 300

# and the comparison run:
CACHE_TYPE=none mvn spring-boot:run
python3 cache_benchmark.py 300
```
