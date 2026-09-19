# Admin Agent — What Was Verified — 2026-09-19

An administrator types one sentence; the agent reads the schedule and comes
back with a plan. Nothing reaches the database until a person approves it.

```text
"Thursday's organics collection is delayed to Friday because of the storm.
 Let residents know."

  → update_collection   Change collection event 6 to a organic collection
                        for sector all on 2026-09-25 (brown bin)
  → create_notice       Publish notice "Collecte des matieres organiques
                        reportee" from 2026-09-19 to 2026-09-26 (visible)

  [ Confirm and apply ]  [ Discard ]
```

## The one design decision everything else follows from

**Reads run. Writes do not.**

The Anthropic SDK ships a tool runner that executes every tool the model
calls, and using it here would have been half the code. It is the wrong shape
for this problem: a tool runner cannot express "look at the real schedule, but
do not touch it". So the loop is written out by hand — read tools execute
immediately, so the model plans against live rows and real ids, while every
write is recorded as a proposed step and handed back to a person.

This is asserted directly rather than described:

```text
AdminAgentServiceTest.recordsWriteToolsInsteadOfRunningThem
  given a canned model response calling create_collection
  then  verify(catalog, never()).executeWrite(any())
```

## The confirmation line is generated from the arguments, not from the model

Asking the model to describe its own steps would produce two independent
outputs — a description and a set of arguments — with nothing forcing them to
agree. An administrator could approve "move Thursday's organics to Friday"
over arguments that say something else entirely.

`StepSummariser` builds the line from the arguments that will actually run. It
cannot misrepresent them, and when the model omits a field the line says so:

```text
delete_collection with no id  →  "Delete collection event ?"
```

## What the agent is allowed to touch

| Resource | In scope | Why |
|---|---|---|
| Collection schedule | yes | Content comes from the administrator's instruction |
| Public notices | yes | Same |
| Sorting-guide entries | **no** | Creating one needs French, English and Chinese names, instructions and search keywords — a model asked for those is inventing municipal facts, which is exactly what [the sorting assistant's design](assistant-results.md) exists to prevent |

Every tool calls the same service method the admin forms call. The agent
inherits their validation, their 404s and their cache eviction, and cannot
acquire a capability the signed-in administrator does not already have.

One gap that had to be closed explicitly: the `@NotNull` annotations on the
request records are applied by Spring at the controller boundary, and the
agent does not cross it. Without an explicit `validator.validate()` call a
plan missing a required field would have reached MyBatis instead of being
rejected. Pinned by test:

```text
create_collection without collectionDate
  → IllegalArgumentException "collectionDate must not be null"
  → 0 rows written
```

## Plans expire, are single-use, and belong to one person

Held in Redis for 15 minutes. An approval is only meaningful against the data
the plan was built from, so a stale plan should disappear rather than stay
executable. Consumed on first read, so a retry after a partial failure has to
be a fresh plan built against the state the failure left behind — not a replay
of steps that may already have run.

```text
planStore.consume(planId, adminId)   → ok
planStore.consume(planId, adminId)   → "expired or was already run"
planStore.consume(planId, otherAdmin) → "expired or was already run"
```

Another administrator's plan reads as not-found rather than forbidden, so plan
ids are not probeable.

## Verified end to end, with the model's part seeded

The build environment has no Anthropic key belonging to the repository owner,
so the planning call could not be made for real. Everything downstream of it
was exercised against the real stack: a plan was written straight into Redis,
the browser was driven to the admin console, and **the approval and the writes
are real**.

```text
before:  collection_event 6  = 2026-09-24, note "Organics are collected on Thursday..."
         special_notice where source_url='agent-demo'  = 0 rows

  [click "Confirmer et appliquer"]  →  POST /api/admin/agent/plans/{id}/execute

after:   collection_event 6  = 2026-09-25, note "Moved to Friday because of the storm."
         special_notice 10   = 2026-09-19 → 2026-09-26, "Organics collection delayed", active
         redis EXISTS agent:plan:{id}  →  0      (consumed)
```

The admin console's own summary cards went from 1 notice to 2 in the second
screenshot below — they reload from the API after execution, so that number is
the write showing up through a separate code path.

> **Stated plainly:** the *proposal* in these screenshots was seeded, not
> produced by Claude. Only `POST /api/admin/agent/plan` was intercepted; the
> approval, the Redis lookup, the validation and the database writes all ran
> for real. What a model does with an instruction is covered separately in
> `AdminAgentServiceTest` with canned responses. The demo rows were reverted
> afterwards. Supply your own `ANTHROPIC_API_KEY` to run the planning half.

| | |
|---|---|
| ![The proposed plan](screenshots/13-agent-plan.png) | ![After approval](screenshots/14-agent-applied.png) |
| A proposal, in amber — nothing has happened yet | After approval: what ran, and the notice count now reading 2 |

## Without an API key

```text
POST /api/admin/agent/plan  (admin token, no ANTHROPIC_API_KEY)
  → 503 "The admin agent needs an Anthropic API key (ANTHROPIC_API_KEY) to be
         configured. Every action it offers is also available through the
         admin forms."
```

![Unconfigured](screenshots/15-agent-unconfigured.png)

503 rather than a rule-based stand-in. Unlike the sorting assistant — whose
retrieval step already does the hard part, so answering without a model is
genuinely useful rather than a pretence — there is no honest non-model version
of "read this sentence and work out which rows to change". A few `if`
statements wearing the agent's name would mislead exactly the person trying to
evaluate whether it works.

## Authorization

Verified against the running server, not inferred from the config:

```text
POST /api/admin/agent/plan   no token         → 401
POST /api/admin/agent/plan   resident token   → 403
POST /api/admin/agent/plan   admin token      → 503 (reached the handler)
POST /api/admin/agent/plan   blank instruction → 400
```

The agent sits under `/api/admin/**`, so Spring Security requires the ADMIN
role before any of this code runs — the same rule that protects the forms.

## A bug the tests caught

Writing the loop test found one that would have broken every write the agent
proposed:

```java
// wrong - toString() is a debug representation, not JSON.
// Parses fine for an empty tool input; fails the moment a call has arguments.
objectMapper.readValue(use._input().toString(), ...)

// right
use._input().convert(new TypeReference<Map<String, Object>>() {})
```

It passed with `list_collections` (whose input is `{}`) and failed with
`create_collection`. A test that only exercised the read path would have
missed it.

## Tests

```text
mvn test                    38/38 passing (unit, no infrastructure)
mvn test -Pintegration-test 64/64 passing (38 unit + 26 integration, real MySQL + Redis)
```

| Suite | Covers |
|---|---|
| `AdminAgentServiceTest` (4) | writes recorded not executed, reads executed, empty plan has no id, turn ceiling |
| `StepSummariserTest` (4) | the confirmation line, including missing arguments |
| `AdminAgentIntegrationTest` (7) | 503 wording, RBAC, real writes, single-use plans, plan ownership, validation, live reads |

The execute half needs no model at all — approval and execution are ordinary
requests carrying a plan id — so it is fully covered against real MySQL and
Redis without spending a token.
