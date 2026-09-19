# Kafka, the Outbox, and the Audit Trail — 2026-09-19

A reviewer of this project made two fair objections before any of this was
built:

> Two consumers alone do not establish a need for Kafka. For this app, a MySQL
> outbox and a worker are a simpler starting point. A future broker design
> must address database/event atomicity, duplicate delivery, idempotency and
> retries before claiming reliable notifications or audit coverage.

> Heterogeneous audit snapshots alone do not justify another database. MySQL
> supports a native JSON type.

Both are right, and this page is the answer to them rather than a rebuttal.
The atomicity, duplicate-delivery, idempotency and retry questions are
answered below with code and tests. The MongoDB question is answered by
implementing the audit trail **twice** and letting the comparison speak —
which is why MySQL is the default.

## The dual-write bug this is built to avoid

The obvious way to publish an event is to call the broker from the service
method:

```java
// wrong
noticeMapper.insert(notice);
kafka.send(topic, event);     // ← different system, no shared transaction
```

There is no transaction spanning those two lines. Either can succeed while the
other fails, and both failure directions are real:

- broker succeeds, database rolls back → residents are told about a notice
  that does not exist
- database commits, broker call fails → a notice exists that nobody is told
  about, and nothing in the system knows

Retrying does not fix it, because the failure is in the gap between two
systems. So the event is written to a **MySQL table in the same transaction as
the notice**, and a relay moves committed rows to Kafka afterwards:

```java
@Transactional
public SpecialNotice create(NoticeRequest request) {
    ...
    SpecialNotice created = mapper.findById(generatedId).orElseThrow();
    outbox.recordNoticeEvent("created", generatedId, currentActor(), null, created);
    return created;   // both rows commit, or neither exists
}
```

Asserted directly, by observing the database and the broker separately:

```text
NoticeEventPipelineIntegrationTest.theEventIsCommittedWithTheNoticeAndOnlyThenReachesTheBroker

  notices.create(...)
    → outbox row exists, event_type = "notice.created", published_at IS NULL
    → countUnpublished() == before + 1        (committed, not yet sent)

  relay.publishPending()
    → countUnpublished() == before            (sent, and only then marked)
```

The test drains the relay by hand rather than waiting for the scheduler,
because the interesting state is the one *between* the commit and the publish.

## What the trade costs, and where it is paid

The relay marks a row published only after the broker acknowledges it. A crash
between the send and the mark therefore **replays** the event rather than
losing it — the safe direction. That makes delivery at-least-once, so
exactly-once *delivery* is replaced by exactly-once *effect*, enforced on the
consumer:

```java
// MongoDB                                  // MySQL
mongo.upsert(                               insert into audit_record (...)
    Query.query(where("_id").is(eventId)),  values (...)
    new Update().setOnInsert(...),          on duplicate key update event_id = event_id
    COLLECTION);
```

Both are a single write. A read-then-insert would let two consumers replaying
the same event concurrently both decide it is new; an upsert and an
`ON DUPLICATE KEY` cannot.

```text
AuditStoreIntegrationTest.isIdempotentOnTheEventId[mysql-json]  ✓
AuditStoreIntegrationTest.isIdempotentOnTheEventId[mongodb]     ✓
NoticeEventPipelineIntegrationTest.replayingAnEventDoesNotDoubleCountIt  ✓
```

Ordering is handled by keying each message on the aggregate id, so every event
about one notice lands on the same partition; and the relay stops at the first
failure rather than skipping ahead, because that guarantee only holds if a
stuck event blocks the ones behind it.

## What the broker actually buys

Honestly: with exactly the two consumers that exist today, a worker thread
reading the same outbox table would do the same job with one fewer system to
operate. The reviewer was right about that.

What a broker adds is that the two consumer groups are **independent** — each
tracks its own offset, so a slow or broken notification path cannot stall the
audit trail — and that a third consumer subscribes without anyone editing the
producer. Whether that is worth operating Kafka depends on whether the third
consumer ever arrives. The independence is real and is tested:

```text
NoticeEventPipelineIntegrationTest.bothConsumerGroupsReceiveTheSameEvent
  one notice.created event
    → group "audit-writer"       writes the audit record
    → group "notification-outbox" counts residents with reminders enabled
```

The second consumer deliberately stops at counting. This project has no push
channel, and inventing one to justify the consumer would be exactly the kind
of thing the rest of this codebase argues against.

## Why MySQL is the default audit store

`AuditStore` has two implementations, and the same contract test runs against
both:

```text
AuditStoreIntegrationTest — 6 tests, 3 cases × 2 backends

  keepsBothSidesOfAChangeAndReadsThemBack   [mysql-json] ✓  [mongodb] ✓
  isIdempotentOnTheEventId                  [mysql-json] ✓  [mongodb] ✓
  handlesTheNullSideOfCreationsAndDeletions [mysql-json] ✓  [mongodb] ✓
```

That result *is* the finding. If the document store were carrying its weight
at this scale, one implementation would struggle where the other did not.
Neither does — the two are about the same length and the same shape — so the
audit trail defaults to the database this application already runs, and
`AUDIT_STORE=mongodb` switches it.

What would actually justify MongoDB here: querying *into* the snapshots across
heterogeneous shapes ("every change where the French title mentioned a
storm"), retention measured in years, or a write rate that makes an
append-only collection with its own lifecycle attractive. None are true yet.
The one place the document model does read better is that `before` and `after`
are whole entity snapshots of varying shape, and they go in as they are.

## The failure that is otherwise silent

`GET /api/admin/audit/status` reports `pendingEvents`. Because the outbox
commits with the business write, a figure that keeps climbing means the relay
or the broker has stopped **while the application carries on accepting changes
perfectly well**. Nothing else in the system would notice.

```json
{"auditBackend": "mysql-json", "auditRecords": 12, "pendingEvents": 0}
```

## How this was verified without Docker

Docker is unavailable in the environment this was built in, so neither a Kafka
container nor a `mongod` container was an option — and the usual embedded
MongoDB library downloads a real `mongod` binary, which CI should not have to
depend on either.

- **Kafka**: `@EmbeddedKafka` — a real broker, in the test JVM.
- **MongoDB**: `mongo-java-server` — a real wire-protocol implementation, in
  the test JVM, driven through the real Spring Data MongoDB driver. Only the
  storage engine underneath differs.
- **MySQL**: the real thing, as everywhere else in this suite.

Both are ordinary jars, so CI needs no new service containers for any of this.

```text
mvn test                    46/46 passing (unit, no infrastructure)
mvn test -Pintegration-test 84/84 passing (46 unit + 38 integration)
```

## Reproducing it

```bash
docker compose up -d                      # now also starts Kafka and MongoDB
cd backend && mvn test -Pintegration-test

# switch the audit trail to the document store and re-run the same tests
AUDIT_STORE=mongodb mvn test -Pintegration-test
```
