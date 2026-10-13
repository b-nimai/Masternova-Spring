# ADR-0012 — SeaweedFS as the local S3 server (MinIO's replacement)

**Status:** accepted · **Date:** 2026-10-13 · **Deciders:** Nimai
**Context links:** [`docs/lld/media.md`](../lld/media.md) · [`docs/media/s3_probe.py`](../media/s3_probe.py) · `compose.yaml` (`seaweedfs`, `media-init`)

## Context

Dev and CI need an S3-compatible server. Production is real S3 (D6) or whatever the cluster
provides (D4). Since 2026-10-02 MinIO doesn't publish container images (`minio/minio` and
`quay.io/minio/*` can't be pulled), so compose kept it as an opt-in profile that only worked from
a cached image.

The application talks to storage only through the **AWS SDK v2** with an endpoint override
(media LLD §4). So the server is a dev and CI tool, not a dependency. What it must get right is
exactly what the **browser** uploader relies on, because that is the part a misbehaving dev server
would let through to production unnoticed:

1. presigned **multipart** uploads (`CreateMultipartUpload`, presigned `UploadPart`,
   `CompleteMultipartUpload`, `AbortMultipartUpload`);
2. **`ListParts`**, because resume asks the provider ([ADR-0014](0014-upload-progress-from-the-provider.md));
3. **bucket CORS** set through the S3 API: the allowed origins get the presigned `PUT`, their
   response headers can be exposed (`ETag`: the uploader logs it per part; completion doesn't
   depend on it, because the server builds it from `ListParts`, ADR-0014), and foreign origins
   are refused, **as AWS does**;
4. SigV4 presigned URLs actually verified (a tampered URL fails);
5. ranged `GET` (HLS players and `ffprobe` read byte ranges);
6. `ListMultipartUploads` (the orphan sweeper, 7.6);
7. one-command start in compose, a healthcheck, settings that survive a restart, an image that is
   still published.

## Measurements

`docs/media/s3_probe.py` runs the flow above against each candidate on 2026-10-13 (arm64, Docker
29.7.2). A 5 MiB part plus a 1,234 B part. The browser is simulated with an `Origin` header.

| Check | SeaweedFS 4.48 | RustFS 1.0.1 | Garage 2.1.0 | LocalStack (latest) |
|---|---|---|---|---|
| Image pullable | ✅ | ✅ | ✅ | ✅ but **exits 55**: "pro features can only be used with a valid license" |
| PutBucketCors / GetBucketCors (`ETag` exposed) | ✅ | ✅ | ✅ | — |
| Preflight from an allowed origin | ✅ 200 | ✅ 200 | ✅ 200 | — |
| Preflight from a **foreign** origin | ✅ **403** (like AWS) | ✅ refused | ❌ **`Allow-Origin: *`** | — |
| Presigned `UploadPart` from the browser, `ETag` readable | ✅ | ✅ | ✅ | — |
| Tampered / wrong-secret / unsigned URL rejected | ✅ 403 | ✅ 400 (malformed) / 403 | ✅ 403 | — |
| `ListParts` → `[(1, 5242880), (2, 1234)]` | ✅ | ✅ | ✅ | — |
| Complete + size matches, ranged GET → 206 | ✅ | ✅ | ✅ | — |
| `ListMultipartUploads` + `Abort` | ✅ | ✅ | ✅ | — |
| CORS + objects survive a restart | ✅ | ✅ | ✅ | — |
| Start in compose | one command, credentials from env | one command, credentials from env | config file + `layout assign` + `layout apply` + generated `GK…` key | — |
| Healthcheck tool in the image | `wget` | `wget` | none (no shell) | — |
| Image / idle RSS | 687 MB / 131 MiB | 360 MB / 106 MiB | **33 MB / 5 MiB** | — |
| Licence · maturity | Apache-2.0 · since 2014 | Apache-2.0 · 1.0 in 2026 | AGPL-3.0 · since 2020 | — |

## Decision

**SeaweedFS** (`chrislusf/seaweedfs:4.48`, `weed server -s3`), with a one-shot `media-init` job
(`amazon/aws-cli:2.31.0`) that creates the bucket and puts the CORS rule **through the S3 API**,
the same call Terraform will make against real S3 in D6.

1. **It behaves like AWS where it matters.** All checks pass, including refusing a foreign
   origin's preflight. Garage answers that preflight with `*`, so a CORS bug that AWS would block
   would pass in dev and in CI's e2e. That fails requirement 3, and it's the only requirement that
   tests the browser path.
2. **Lowest vendor risk of the three.** It's the oldest project, Apache-2.0, and it has more than
   one way to run it (single server, filer, cluster). RustFS works today and is the nearest
   MinIO look-alike, but it reached 1.0 only this year. Picking the young single-vendor server is
   how the MinIO problem happened in the first place.
3. **It's the simplest to run:** credentials come from env, so there's no key-generation step to
   script, and `wget` is there for the healthcheck.

## Consequences

- **Positive:**
  - `docker compose --profile media up -d` works on a clean machine and in CI again.
  - The CORS rule is code (`media-init`) in the same S3 API shape as production.
  - Because everything goes through the AWS SDK with an endpoint override, replacing SeaweedFS
    later is a compose change only: no Java changes.
- **Negative:**
  - The image is the largest of the three (687 MB). That's a one-time pull for dev and CI; it is
    never shipped.
  - Its S3 support is a compatibility layer over its own filer, so newer S3 features (object lock,
    checksums on complete, lifecycle rules) may lag. Phase 7 needs none of them. The orphan
    sweeper (7.6) is our own code, not a lifecycle rule, which is why that doesn't matter.
  - The dev credentials (`masternova` / `masternova-dev-secret`) are in `compose.yaml`. They are
    dev-only, like Postgres's, and the stack's real secrets are still files from `make secrets`.

## Alternatives rejected

| Option | Why not |
|---|---|
| **RustFS** | Passes every check and is the closest drop-in for MinIO. Rejected on maturity: 1.0 came out in 2026, and a young project run by one vendor is the exact risk that just cost us MinIO. It's the first fallback if SeaweedFS goes the same way. |
| **Garage** | The smallest by far (33 MB, 5 MiB RSS), but it allows foreign-origin preflights (`*`), needs a layout + key bootstrap, and has no shell for a healthcheck. Its strength (geo-distributed self-hosting) isn't our problem. |
| **LocalStack** | The image now needs a licence to start. It also emulates far more than S3, which we don't need. |
| **MinIO from a cached image / building it from source** | The cache doesn't exist on new machines or CI runners, and building it ourselves would make us its maintainers. |
| **Real S3 in dev** | Needs an AWS account and costs money for every developer and CI run, and the tests depend on the network. D6 covers the real thing. |
