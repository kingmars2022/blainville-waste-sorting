# S3, Lambda and API Gateway — What Ran — 2026-09-19

A resident photographs something at the curb and asks which bin it goes in.
The photo goes straight from their phone to S3, a Lambda strips its metadata
and resizes it, and only that stripped copy is ever served.

```text
browser ──presigned PUT──▶ s3://…/original/{id}      (bytes never touch the app)
                                   │
                          ObjectCreated event
                                   ▼
                          Lambda: decode → resize → re-encode
                                   │
                           s3://…/processed/{id}
                                   ├──▶ API Gateway ──▶ the resident sees the photo
                                   │
                                   └──▶ vision model ──▶ "boîte à pizza"
                                                              │
                                              the same retrieval a typed question uses
                                                              ▼
                                                  guide entry ──▶ bin, instruction, source
```

## What the pipeline is for

Worth stating plainly, because infrastructure without a use is just
infrastructure: the photo exists so a resident who does not know what
something is called can still get an answer. `POST /api/photos/{id}/identify`
reads the processed copy, asks a vision model **what the object is**, and
feeds that name into the retrieval the text assistant already uses.

**The model names the object; the guide decides the bin.** A vision model
asked "which bin?" would answer from whatever it absorbed about recycling in
general, and Blainville's rules are not general — soiled cardboard goes in the
brown bin here and the black bin in plenty of other municipalities. The
response schema has nowhere to put a bin colour, so the constraint is
structural rather than a prompt the model might drift from:

```java
public record MaterialIdentification(
        String material,             // "boîte à pizza"
        List<String> alternateTerms, // synonyms, to widen the guide search
        boolean uncertain            // too blurry / too ambiguous to name
) { }
```

The photo path therefore inherits the text assistant's guarantees, refusal
included. Three outcomes, three different things to tell the resident:

| Outcome | What they see |
|---|---|
| Recognised, and in the guide | The bin, the instruction, and the entry it came from |
| Recognised, not in the guide | *"I recognised «aquarium», but that is not in the Blainville guide"* |
| Not recognised | *"I can't make out what that is — try a sharper photo"* |

Naming what it saw even when the answer fails is the difference between
*retake the photo* and *phone the city*. Pinned by
`PhotoSortingServiceTest`, including the assertion that the composer is never
reached when retrieval finds nothing.



## The part that matters: GPS coordinates

A photo taken at the curb outside someone's house carries their home address
in its EXIF GPS tags, to within a few metres, along with the device model and
an exact timestamp. Serving that image back — or simply keeping it — publishes
an address nobody agreed to share.

Stripping it is a re-encode: decode to pixels, write a fresh JPEG, and every
metadata segment is gone. The claim is easy to make and easy to break, so it
is asserted by reading the metadata back out of a real JPEG built with a real
EXIF APP1 segment:

```text
PhotoProcessorTest.stripsGpsCoordinatesFromThePhoto
  fixture  → GpsDirectory present      (the fixture is genuinely incriminating)
  process  → GpsDirectory absent

PhotoProcessorTest.stripsMetadataEvenWhenThePhotoIsAlreadySmallEnoughToSkipResizing
  320×240 photo, below the resize threshold
  fixture  → GpsDirectory present
  process  → GpsDirectory absent
```

The second test exists because of the optimisation somebody will eventually
propose: *"it's already under 1024px, just copy it."* A small photo carries
exactly the same GPS tags as a large one, so that shortcut would silently
reintroduce the leak for every screenshot and every already-resized upload.

## Why the bytes do not come through the application

A phone photo is a few megabytes. Accepting it as a multipart POST means every
upload holds a request thread and a slice of heap for as long as the
resident's mobile connection takes — the slowest clients cost the most, which
is backwards. A presigned PUT means the browser talks to S3 directly and this
application only ever handles a signature.

The constraints are **signed into the URL**, not merely checked before issuing
it, because a limit enforced only by the application is a limit a caller skips
by not calling it:

```text
X-Amz-SignedHeaders=content-length;content-type;host
X-Amz-Expires=300
```

> Verified: that the size, the type and an expiry are part of the signature.
> Not verified locally: that the server rejects a mismatched body. That is
> real S3's behaviour, and the local S3 implementation used here does not
> simulate it.

## The full round trip, against a real S3 API

```text
PhotoPipelineIntegrationTest.aResidentUploadsStraightToS3AndOnlyTheStrippedCopyIsServed

  POST /api/photos/upload-url        → 200, {photoId, uploadUrl, expiresIn}
  HTTP PUT to uploadUrl              → 200   (plain java.net.http client,
                                              knows nothing about this app)
  GET /api/photos/{id}               → 404   (nothing processed yet - the
                                              original is never reachable)
  [S3 ObjectCreated → Lambda]
  GET /api/photos/{id}               → 200, image/jpeg
        original  → GpsDirectory present
        served    → GpsDirectory absent
        served.length < original.length
```

The 404 in the middle is the privacy design stated as a test: between upload
and processing there is simply nothing to serve, because no route reads the
`original/` prefix.

Other properties pinned by the same suite:

| Test | Why it exists |
|---|---|
| `theOriginalStaysInTheBucketAndIsNeverServed` | Kept for reprocessing, unreachable by any route |
| `theLambdaIgnoresItsOwnOutput` | A function triggered by the prefix it writes to re-triggers itself, and bills until someone notices |
| `refusesAFileTypeItWillNotProcess` | Rejected *before* a signature exists — never grant permission to upload something you will not process |
| `refusesAPhotoLargerThanTheLimit` | 413 rather than a signed URL |
| `rejectsAPhotoIdThatIsNotOne` | `../../etc/passwd` and `not-a-uuid` both 400; an id parameter is otherwise a path into the bucket |

## What ran and what did not

Docker is unavailable in the environment this was built in, so LocalStack was
not an option. The substitutions and their limits:

| Piece | How it was exercised | Real? |
|---|---|---|
| Presigned URL | Produced by the AWS SDK, sent as an ordinary HTTP PUT | **Yes** — real signature, real HTTP |
| S3 API | `s3mock` — a real S3 HTTP API as a jar | **Yes**, except content-length enforcement |
| Lambda handler | Invoked directly with the `S3Event` S3 would send | **Yes** — real handler, real S3 client, real bytes |
| Lambda *runtime* | — | **No.** Cold starts, timeouts, memory limits and IAM are untested |
| S3 → Lambda trigger | The event was constructed in the test | **No.** AWS's delivery, retries and dead-lettering are untested |
| API Gateway | Declared in `infra/template.yaml`, parsed as YAML | **No.** Never deployed |

That last column is the honest part. The image processing, the signing, the
prefix isolation and the request validation all ran for real. The AWS-managed
glue — event delivery, the function runtime, the gateway — is written and
reviewable but was never executed, and no claim here should be read as saying
otherwise.

## Decisions visible in the template

`infra/template.yaml` is a SAM template. The parts worth reading:

- **Originals expire after 7 days.** They still carry the EXIF the processor
  strips. They are kept only long enough to reprocess after a bug, because the
  cheapest way not to hold someone's address is not to hold it.
- **The function's IAM policy is `GetObject` on `original/*` and `PutObject`
  on `processed/*`** — not `s3:*` on the bucket. An EXIF stripper that can
  delete photos has a bigger blast radius than the job needs.
- **The S3 notification filters on the `original/` prefix**, which is what
  stops the self-trigger loop at the infrastructure level as well as in code.
- **API Gateway proxies S3 directly**, with the `processed/` prefix hard-coded
  into the integration URI and the photo-id path parameter constrained by
  regex. Serving a photo is a bucket read with a cache header on it; it does
  not need to wake a container.
- **CORS allows `PUT` from one origin.** A wildcard would let any page on the
  internet spend this bucket's storage.

## In the browser

| | |
|---|---|
| ![Photo question](screenshots/16-photo-ask.png) | ![Photo answer](screenshots/17-photo-answer.png) |
| "Or take a photo", under the typed question | Recognised as *boîte à pizza*, answered from the guide entry, sourced |

`capture="environment"` asks a phone for the rear camera directly, which is
the device this feature is for. The provider line reads `vision+template`,
which is the two-stage design made visible: the vision model named it, the
guide answered it.

> **Stated plainly:** this environment has no AWS account and no Anthropic
> key, so the two calls that need them were answered locally for the
> screenshot. Everything else on the page is the real application — the file
> input, the size and type checks, the request sequence, the rendering, the
> source links. The pipeline itself is covered against a real S3 API by
> `PhotoPipelineIntegrationTest`, and the identify logic by
> `PhotoSortingServiceTest`. No live vision call has been made.

## Tests

```text
mvn test                    57/57 passing (unit, no infrastructure)
mvn test -Pintegration-test 102/102 passing (57 unit + 45 integration)
```

## A bug this suite hid from itself

The first CI run of this suite failed where every local run had passed, and
the reason was the test S3 server rather than the code under test. S3Mock is
itself a Spring Boot application, so starting it runs Spring Boot's
auto-configuration against this project's classpath: it built the
application's MySQL DataSource and ran Flyway against whatever
`application.yml` pointed at. On a developer machine whose MySQL accepts
those credentials that is invisible — it quietly migrates the dev database
from a test run. In CI, where the test user is granted only the test
database, it failed the context and took all seven tests with it.

The fix is one property on the S3 server (`spring.autoconfigure.exclude` for
the JDBC and Flyway auto-configuration; MyBatis backs off on its own once
there is no DataSource bean). `LocalS3Test` now guards it, deliberately
untagged so it runs in the plain `mvn test` profile where no database exists
at all — the cheapest possible reproduction of what CI saw.

## Deploying it for real

```bash
cd backend && mvn -DskipTests package
sam deploy --guided \
  --template infra/template.yaml \
  --parameter-overrides BucketName=your-bucket AllowedOrigin=https://your-site

# then point the application at it
PHOTO_BUCKET=your-bucket AWS_REGION=ca-central-1 mvn spring-boot:run
```

No AWS credentials are configured in this repository. The application reads
the default provider chain — environment, container role, instance profile —
so a deployment supplies them and a clone has none to leak.
