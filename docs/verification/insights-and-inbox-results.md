# Giving the Unused Technologies a Job — 2026-09-19

An honest audit of this project found three things carrying no real workload:
MongoDB was implemented but never the default, the second Kafka consumer only
logged a number, and repeat questions paid full price every time. This is what
they do now.

## 1. The city finds out what it has not written down

**The gap this closes.** A resident asked "où jeter un aquarium ?", the guide
had no entry, and the assistant politely said to check blainville.ca. Then the
question vanished. Nobody at the city ever learned that fourteen people asked
about aquariums last month — which is exactly the list somebody needs in order
to decide what to write next.

Every question now goes to a Kafka topic, and a consumer turns the pile into a
work list:

```text
GET /api/admin/insights/gaps?days=30

[ { "term": "aquarium", "asks": 3,
    "languages": ["en", "fr"],
    "examples": ["aquarium", "vieil aquarium en verre"] } ]
```

```text
GET /api/admin/insights/summary?days=30

{ "questions": 2, "answered": 1, "unanswered": 1, "unansweredRate": 50.0 }
```

`unansweredRate` is the number an administrator watches: rising means the
guide is drifting behind what residents actually own.

### Why this workload wants MongoDB, when the audit trail did not

The [audit trail comparison](events-and-audit-results.md) found MySQL's JSON
column did that job just as well, so MySQL stayed the default. This workload
is different in four ways that matter:

| | Audit trail | Resident queries |
|---|---|---|
| Shape | One shape per entity | Differs by source — a typed question and a photo identification carry different fields |
| Reads | Lookup by entity id | **Aggregation**: group, rank, sample |
| Writes | One per admin action | One per resident question |
| Lifetime | Kept | **Expires at 180 days**, by TTL index |

The gap report is one aggregation pipeline — group by term, count, collect the
languages, sample the original phrasings. In SQL that is a `GROUP BY` with a
correlated sample, which is doable and considerably less pleasant. And the TTL
index drops old questions with no scheduled job: a question from two years ago
says nothing about what the guide is missing today, and keeping it is a
liability rather than an asset.

### Why Kafka, and not just a write

Because the analytics write must never cost a resident anything. If MongoDB is
slow, full, or down, questions queue on the topic and residents never notice.
Writing the row inline would put a second datastore on the critical path of
every question, to serve a monthly report.

**This is deliberately the opposite of the notice outbox, in the same
codebase.** A notice event must never be lost, so it is written inside the
database transaction and relayed afterwards. An analytics event must never
slow anything down, so it is fired and forgotten — no transaction, no retry,
no waiting for an acknowledgement, every failure swallowed. Picking one
mechanism for both would get one of them wrong.

### Two bugs this found, both about that promise

Writing it is one thing; the promise is "never costs a resident anything", and
two things broke it:

1. **The producer ignored `app.events.enabled`.** The relay and the consumers
   honoured the switch; the publisher did not. A deployment without Kafka had
   the producer retrying a dead broker once per question — 162 connection
   failures in one test run before this was caught.

2. **`max.block.ms` was unset, so Kafka's default of 60 seconds applied.**
   `send()` blocks for that long while broker metadata is unavailable, and this
   send happens inside a request. An unreachable broker would have stalled a
   resident's question for a minute. Now bounded to one second.

Same class of bug as [the Lettuce one](redis-cache-results.md): a library
default measured in tens of seconds, sitting on a request path, invisible
until something is actually switched off.

A third came out of writing the test: serialization failures and send failures
were both swallowed at DEBUG. An unreachable broker is an operational
condition that resolves itself; an event that cannot be serialized is a bug
that would empty the gap report in complete silence. They are logged
differently now.

### No resident is identified

No IP, no account id, no photo — the question and what the guide did with it
is the whole record. Asserted, not just intended:

```text
ResidentQueryInsightsIntegrationTest.theStreamNeverCarriesAnythingThatIdentifiesAResident
  stored document keys do not contain: ip, remoteAddr, userId, email, photoId
```

The only use for this data is deciding which entries to write next. Anything
more would be collecting for its own sake.

## 2. The second consumer finally delivers something

It used to count recipients and log the number, because this application had
no push channel — and inventing one to justify the consumer would have been
backwards. An **in-app inbox** needs no external service, so the consumer now
does the real thing: one published notice becomes one row per resident who
asked to be told, and they see it on the home page above their schedule.

The fan-out is a single `INSERT ... SELECT` rather than a loop, because the
recipient list is a query the database can answer.

**Deduplication moved with it**, from an in-memory set to a unique key on
`(user_id, event_id)` with `INSERT IGNORE`. The in-memory version reset on
restart — precisely when a consumer is most likely to replay from its last
committed offset, so the old guard was weakest exactly when it was needed.

The notice text is **joined at read time**, not copied into the row, so a
correction reaches everyone who has not read it yet. That is the opposite
choice from the audit trail, where the snapshot *is* the record: one is
history, this is a current message.

## 3. Asking the same thing twice stops costing twice

A municipal sorting guide gets the same twenty questions over and over — it is
a guide to the materials people are unsure about. Every repeat used to re-run
retrieval and, with Claude configured, buy another model call to produce
wording that was already known.

Answers are now cached in Redis. The key is the interesting part:

- the **normalized** question, so "Boîte à pizza ?" and "boite a pizza" are one
  entry rather than two;
- the **language**, because the same words appear in more than one;
- the **composer**, because switching from the template to Claude must not keep
  serving the old wording.

And the question is **hashed** rather than embedded, so residents' text does
not end up legible in whatever tooling lists Redis keys.

### The two traps

**Stale refusals.** A cached "I don't know" for aquariums that survives an
administrator adding the aquarium entry is worse than no cache: the guide
would be right and the assistant would still be wrong. So every sorting-guide
write drops the whole answer cache — not a targeted key, because an edit
changes which entry wins for an unknown set of questions, and a new entry
changes the answer to questions that previously had none.

```text
AssistantIntegrationTest.editingTheSortingGuideDropsCachedAnswers
```

**Under-counting the gap report.** Caching the whole method would have stopped
repeats being recorded — and the report would then systematically under-count
precisely the questions residents ask most, which is the opposite of what it
is for. So a cache hit still publishes its query event, tagged `text-cached`:

```text
AssistantServiceTest.aRepeatQuestionIsServedFromCacheButStillCounted
  cache hit  → retriever.retrieve never called
             → composer never called
             → queries.record("text-cached", ...) still called
```

Refusals are cached too, and they are the cheapest win: the same unlisted
material gets asked about repeatedly, and every repeat otherwise re-runs a
full-text search to find nothing again.

## Verified

Real MySQL, a real Kafka broker, a real MongoDB wire-protocol server, real
Redis — no Docker needed for any of it.

```text
ResidentQueryInsightsIntegrationTest   5 tests   question → topic → Mongo → gap report
NoticeEventPipelineIntegrationTest     5 tests   outbox, relay, both consumer groups, inbox rows
AssistantIntegrationTest              11 tests   including cache hit, normalization, invalidation
QueryEventPublisherTest                4 tests   the gate, and both failure modes
```

---

# The Split That Should Not Have Existed

The audit that produced the three features above also named a defect, and this
is the fix.

## What was wrong

The sorting guide existed **twice**:

| | Read by |
|---|---|
| `frontend/src/data/sortingGuide.ts` | the cards residents actually look at |
| MySQL `sorting_item` + translations + keywords | the assistant, the photo lookup, the admin console |

So an administrator could correct an entry, watch the admin console update,
watch the assistant start giving the new answer — and the page residents open
would still show the old text. The two datasets had also drifted: 12 entries
against 15, with different wording.

## Why it survived this long

The static file held two things the database had no column for:

- `examples` — the short "fruits, legumes, pain" list under each card
- `availability` — seasonal or on-request wording ("free collection in May,
  June and October")

Deleting the file would have silently dropped both. That is why this was a
migration rather than a delete.

## The fix

**V10** adds `availability` to `sorting_item_translation` and a
`sorting_item_example` table — a table rather than a delimited column, because
examples are ordered, per language, and edited one at a time.

**V11** carries the data over, generated from the static file rather than
typed by hand: 192 statements across 12 items and three languages is exactly
the kind of transcription where a silent mistake hides.

**V10 also merges a duplicate.** Rows 4 and 14 were the same municipal service
seeded twice — "Personal documents for shredding" and "Personal document
shredding". Residents saw two cards for one service, and full-text relevance
was split across both, pushing the real entry *down* the results. Row 14 is
strictly richer (it carries the ecocentre address and the actual conditions),
so row 4's keywords were merged into it before it was deleted, rather than
discarding search terms residents might use.

## Verified against the running stack

```text
GET /api/sorting-items            → item 6 fr = "Papiers et cartons souilles d aliments"
                                    examples  = [boite a pizza, assiette en carton, ...]
                                    14 entries

PUT /api/admin/sorting-items/6    → 200   (admin renames it)

GET /api/sorting-items            → item 6 fr = "CARTONS SOUILLES (modifie par admin)"
                                    examples  = [boite a pizza, assiette en carton]
```

The second read is the endpoint the resident page calls. Before this change
that page would still have said the old name.

Then the dev database was dropped and rebuilt from migrations alone, and a
real browser loaded the page with nothing intercepted:

```text
API calls made by the page: [sorting-items]
cards rendered:   14
reminder cards:    5     (the seasonal `availability` entries)
first card:       "Restes de fruits et legumes", 9 examples
same page in zh:  "水果和蔬菜残渣"
shredding entries: 1     (was 2)
```

`SortingGuideIntegrationTest` pins all of it, including the one assertion that
would catch a future split: the number of entries the public endpoint returns
must equal the number of rows the assistant searches.

## Two smaller ones, fixed in passing

- **The photo button said "uploading" for the whole call**, including the
  identification, which is the slower half — so it looked like a stalled
  upload. It now reports the two phases separately.
- **Admin edits would have dropped the new fields.** Adding columns is not
  enough: `SortingItemService` had to persist `examples` and `availability` on
  create and update, or the first admin edit after the migration would have
  wiped exactly the data the migration existed to preserve. Pinned by
  `SortingItemServiceTest.createStoresTheExamplesAndAvailabilityThatUsedToLiveInTheFrontend`.
