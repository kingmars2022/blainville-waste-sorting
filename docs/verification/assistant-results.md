# Grounded Sorting Assistant — Measurements — 2026-09-19

A resident types "où va une boîte à pizza sale ?" and gets the right bin, in
their own language. The answer comes from the municipal sorting guide in
MySQL — never from a model's memory — and when the guide has nothing relevant
the assistant says so.

Everything below is measured against the running application: real MySQL with
the seeded guide, the real full-text indexes from migration V7, real Redis.

## What is being measured, and which number matters

| Metric | Result |
|---|---:|
| Retrieval precision @1 (12 answerable questions, fr/en/zh) | **12 / 12** |
| Refusal accuracy (6 questions the guide cannot answer) | **6 / 6** |

Refusal accuracy is the one that decides whether this is safe to ship. Telling
a resident to put paint in the blue bin is a real-world error with a
real-world consequence, and "the model sounded confident" is not a defence. An
assistant that answers everything scores 100% on precision and is still
unusable.

Reproduce with [`assistant_eval.py`](assistant_eval.py) against a running
backend. The 18 questions are in the script, not hidden in a fixture.

## The architecture that makes refusal possible

Grounding is enforced in `AssistantService`, *above* the thing that writes the
prose: if retrieval returns nothing, no model is called at all. The unit test
asserts the composer is never reached, rather than asserting on wording —
that property then holds no matter which composer is configured, including
ones written later.

```text
question ──▶ QueryNormalizer ──▶ MySQL full-text ──▶ relevance cutoff
                                                            │
                                            empty? ─────────┴──▶ refuse (no model call)
                                                            │
                                                      non-empty ──▶ composer ──▶ answer + sources
```

## Two full-text parsers, because one does not fit three languages

This was the first thing measurement changed. The obvious design — one ngram
index, since ngram is what makes Chinese searchable — produces an assistant
that cannot refuse:

```text
Query: "Comment réparer ma voiture ?"   (the guide has nothing about car repair)

  ngram parser:   Sapins de Noel     4.796
                  Encombrants        4.373      ← higher than some genuine matches
                  Retailles de cedre 2.898

  word parser:    every row          0.000      ← correct: nothing matched
```

The ngram parser tokenizes into character bigrams. For Chinese that is the
whole point — 废电池怎么处理 has no whitespace to split on. For French and
English it means any two adjacent letters can score, so long questions score
against everything regardless of meaning. No relevance threshold fixes that,
relative or absolute: the nonsense query outscored real ones.

So: word parser for French and English, ngram for Chinese. `MATCH()` resolves
its index by column list, so the two cannot share columns — V7 gives the ngram
parser **stored generated columns** of its own, which means nothing in the
application is responsible for keeping them in sync.

## The second false positive: MySQL's stopword list is English-only

With the word parser in place, one refusal case still failed:

```text
Query: "Quel est le numéro de téléphone du maire ?"
  → answered, grounded=true, top source "Ordures menageres", score 2.733

Per-word breakdown:
  quel       0.000
  est        2.733      ← the entire match
  numéro     0.000
  téléphone  0.000
  maire      0.000
```

`est` is a French copula. It is scored as content because InnoDB ships an
English stopword list and nothing told it otherwise. The assistant answered a
question about the mayor's phone number with household-waste advice.

MySQL can be pointed at a custom stopword table, but that is a server variable
read at index-creation time — correct behaviour would then depend on how
someone configured their MySQL, and would regress silently on a machine where
they hadn't. `QueryNormalizer` strips function words in the application
instead, where the list travels with the code and is unit-tested.

A third pass caught `vont`: "Où vont les branches coupées ?" ranked the
green-waste entry above the dedicated Branches entry, because "vont" appears
in the green-waste instruction and the two were within 7% of each other.

## Keywords are additive, and they are what make colloquial input work

```text
Query: "Où va une boîte à pizza sale ?"   (French)

  name + instruction only:   Dechiquetage de documents personnels   2.733   ← wrong
                             Papiers et cartons souilles d aliments 1.828

  + curated keyword score:   Papiers et cartons souilles d aliments 4.744   ← right
                             Dechiquetage de documents personnels   2.733
```

One stray common word is enough to invert the ranking on name and instruction
alone. The `sorting_item_keyword` table an administrator curates per language
is what pulls the right entry back on top — so it is summed in, not used as a
fallback.

## The endpoint that can spend money

This is the only route in the application that can call a paid API, once per
request, without authentication. It carries a per-IP hourly cap counted in
Redis — verified by exhausting it:

```text
redis-cli GET assistant:ratelimit:20260919T04:127.0.0.1   →  31

POST /api/assistant/ask
  → HTTP 429 {"message":"Too many assistant questions from this address..."}
```

**This limiter fails closed while the cache fails open — same Redis, opposite
policy.** The cache protects latency, so losing it must cost speed and never
availability. The limiter protects a budget, so losing it must not silently
remove the only spending cap: if the counter cannot be read, the assistant
returns 503 and the rest of the site is untouched.

The question is capped at 300 characters, because an unbounded question is an
unbounded prompt and the prompt is the part that costs money. The key is the
remote address only — `X-Forwarded-For` is attacker-controlled without a
trusted proxy in front, and a rate limit keyed on a value the caller picks is
not a rate limit.

## Two composers, one interface

| | `template` (default) | `anthropic` |
|---|---|---|
| Needs an API key | no | yes (`ANTHROPIC_API_KEY`) |
| Cost per question | zero | per-token |
| Works on a fresh clone | yes | needs configuration |
| Facts it may use | the retrieved entries | the retrieved entries |

`template` is not a mock. Retrieval has already found the right entry, and
reading that entry back in the resident's language is a genuinely useful
answer — which is why it is the default rather than a placeholder. It also
gives CI something deterministic to assert on, and gives the Claude-backed
composer something to be compared against.

`ClaudeAnswerComposer` falls back to the template on any failure — an outage,
an expired key, a rate limit, or a safety refusal (`stop_reason: "refusal"`
returns HTTP 200 with no usable text, so it is checked before reading
content). The sorting guide degrades to plainer wording, never to an error
page.

> **Verified and not verified.** Everything above was run end to end with the
> `template` composer. The Claude path is exercised by unit tests through the
> `AnswerComposer` interface, but no live Anthropic API call was made — the
> credentials in the environment this was built in belong to the build
> infrastructure, not to the repository owner, and spending someone else's
> budget to produce a screenshot is not a verification. Supply your own key to
> run it.

## In the browser

| | |
|---|---|
| ![French, grounded](screenshots/10-assistant-fr.png) | ![French, refusal](screenshots/11-assistant-refusal.png) |
| Grounded answer, with the guide entry it used | A refusal, deliberately styled differently from an answer |
| ![Chinese](screenshots/12-assistant-zh.png) | |
| The same assistant in Chinese, through the ngram index | |

Sources are shown rather than tucked behind a disclosure: an answer a resident
cannot trace back to the municipal guide is worth less than one they can
check, and showing them makes a bad retrieval obvious instead of invisible.
Captured with [`screenshot_assistant.mjs`](screenshot_assistant.mjs) driving a
real Chromium against the real frontend and backend.

## Tests

```text
mvn test                    30/30 passing (unit, no infrastructure)
mvn test -Pintegration-test 49/49 passing (30 unit + 19 integration, real MySQL + Redis)
```

`AssistantIntegrationTest` pins the properties that must not regress silently:
a grounded answer in each of the three languages, the two false positives
above, the 300-character cap, and the rate limit. Retrieval quality cannot be
asserted with mocks — the feature *is* the interaction between two full-text
parsers, a curated keyword table, and the stopword filter in front of them.
