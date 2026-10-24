# Security scans: secrets, DAST, review

**Last updated:** 2026-10-24 (D5.5) · **Status:** in CI

Three checks, each aimed at a different way a vulnerability ships:

| Check | Catches | Runs | Where |
|---|---|---|---|
| **gitleaks** | a credential committed, now or ever | every CI run, **the whole history** | `make secrets-scan`, CI job `secrets`, [`.gitleaks.toml`](../../.gitleaks.toml) |
| **OWASP ZAP baseline** (DAST) | what the *running* app sends: missing headers, leaks, cookie flags | CI `e2e` job, after Playwright, on the same compose stack | `make zap`, [`.zap/rules.tsv`](../../.zap/rules.tsv) |
| **Security review** of the phase diff | injection, authz, secrets in code, data exposure in new code | at each phase close (the `security-review` skill) | the PR |
| Trivy *(since D1)* | known CVEs in the images | CI `images` job | `make scan` |
| CodeQL *(since D2)* | data-flow bugs in Java and TypeScript | `codeql.yml` | GitHub code scanning |

## 1. gitleaks: a scan that passed by scanning nothing

The first run of `gitleaks git` in Docker printed **"0 commits scanned … no leaks found"** and
exited 0. Git refused the mounted repository (its owner differs from the container's user:
"dubious ownership"), and gitleaks treated the error as an empty history.

- Fix: `safe.directory` through git's environment config (`GIT_CONFIG_COUNT` / `KEY_0` /
  `VALUE_0`), so no config file is needed in the image.
- Guard: `make secrets-scan` also **fails if the log says "0 commits scanned"**. A security check
  that can pass by doing nothing needs a check on the check.
- Now: **199 commits, no leaks.** The one allowlist is `AND`-scoped: a path under `/src/test/`
  **and** a value shaped `test-…secret-<hex>` (the HMAC fixtures). A real-looking key in a test,
  or a fixture-shaped value in main code, still fails.

## 2. ZAP: what the browser actually receives

The baseline spiders the app and applies passive rules only (no attacks). The first scan
against the cluster, before any change:

| ZAP rule | Before | Fix |
|---|---|---|
| 10038 Content-Security-Policy | missing | a CSP, source by source (below) |
| 10020 anti-clickjacking | missing | `frame-ancestors 'none'` + `X-Frame-Options: DENY` |
| 10021 `X-Content-Type-Options` | missing | `nosniff` |
| 10063 Permissions-Policy | missing | camera, mic and geolocation off; `payment` for Razorpay only |
| 10035 HSTS | missing | `max-age=31536000` (ignored over plain http, honoured behind Traefik's TLS) |
| 10036 `Server` leaks the version | `nginx/1.31.x` | `server_tokens off` |

After: **0 FAIL, 59 PASS**, three accepted WARNs, each with its reason in `rules.tsv`:
`style-src 'unsafe-inline'` (Angular inserts component styles at runtime), "dangerous JS
functions" and "suspicious comments" (both in the minified framework bundle). The six rules
above are **FAIL** now: a regression breaks CI.

**The CSP, and why each source is there** ([`security-headers.conf.template`](../../frontend/security-headers.conf.template)):

```
default-src 'self'
script-src  'self' https://checkout.razorpay.com            ← no 'unsafe-inline' (below)
style-src   'self' 'unsafe-inline' https://fonts.googleapis.com
font-src    'self' https://fonts.gstatic.com
img-src     'self' data: blob: ${STORAGE_ORIGIN} https://*.razorpay.com
media-src   'self' blob: ${STORAGE_ORIGIN}                  ← hls.js feeds <video> via MSE
connect-src 'self' ${STORAGE_ORIGIN} https://*.razorpay.com ← presigned uploads + segments
worker-src  'self' blob:                                    ← hls.js's transmux worker
frame-src   https://api.razorpay.com https://checkout.razorpay.com
frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'
```

Two design points:

- **No inline script, on purpose.** Angular's critical-CSS inlining adds an inline
  `<script>` that swaps the stylesheet's `media` after load. Allowing it needs `'unsafe-inline'`
  (which defeats the CSP) or a hash that breaks whenever the snippet changes. Critical inlining is
  off instead (`angular.json`); the cost is one render-blocking stylesheet.
- **One image, every environment.** The storage origin differs (compose
  `http://localhost:9010`, the cluster `https://s3.masternova.localhost:8443`). nginx's image
  renders `*.template` files with `envsubst` at start; the output goes to `/var/run` (the only
  writable path in a read-only container), limited to `STORAGE_ORIGIN` so nginx's own `$uri`
  survives. A location with its own `add_header` drops every inherited one, so each location
  includes the headers file.

## 3. Review of the phase diff

`security-review` on everything D5 changed: **no high-confidence finding.** Ten candidates were
examined and rejected (the bulkhead runs before authentication: DoS, out of scope and deliberate;
the backup S3 keys are created by `make k8s-secrets`, never templated: the first version rendered them in the chart, and Argo CD's project, which refuses Secrets from git, failed the sync after the merge (fixed in a follow-up PR); a 503 on a lost
connection releases the idempotency key, as every 5xx already did; …). The phase *removed* the
header gaps above and a secrets scan that scanned nothing.

## 4. Reproduce

```bash
make secrets-scan                    # whole history; fails on a leak OR on "0 commits scanned"
make stack && make zap               # .zap/report.html; ZAP_TARGET=https://… for another target
```
