# 11 — HMAC & signed tokens: proving a link came from you, without storing it

> **One-liner:** an HMAC is a **keyed hash**. Only someone holding the secret can produce the tag
> for a message, so `message + tag` is a token anyone can carry and only you can mint or check.
> No database row per token.

**Last updated:** 2026-10-02 · **Roadmap:** 4.6 (later 8.5 playback tokens, 9.4 Razorpay webhook signatures)
**Real code:**
- [`UnsubscribeTokens`](../../backend/kernel/src/main/java/com/masternova/kernel/notification/UnsubscribeTokens.java) (kernel, plain Java), with tests in [`UnsubscribeTokensTest`](../../backend/kernel/src/test/java/com/masternova/kernel/notification/UnsubscribeTokensTest.java).
- **Issuer:** the worker's [`UnsubscribeLinks`](../../backend/worker/src/main/java/com/masternova/worker/notification/UnsubscribeLinks.java).
- **Verifier:** the api's [`NotificationPreferencesService.unsubscribe`](../../backend/api/src/main/java/com/masternova/api/notification/application/NotificationPreferencesService.java), proven over HTTP by `NotificationPreferencesIT`.

**Design:** [`docs/lld/notification.md`](../../docs/lld/notification.md) §5 · **Related:** JWT HS256 in identity is the same primitive (note 08 config, ADR-0006)

**Priority marks:** ⭐⭐⭐ must know · ⭐⭐ use daily · ⭐ good to know.

---

## 1. The problem ⭐⭐⭐

Every opt-out-able email must carry an unsubscribe link that:

- works **without logging in** (people read mail on another device);
- unsubscribes **exactly one user from exactly one category**;
- can't be **edited** into "unsubscribe someone else" or "unsubscribe from receipts";
- doesn't need a **database row per email** (millions of emails, almost none clicked).

`/unsubscribe?user=42&category=PRODUCT_NEWS` fails the third point: anyone can change `42`. A
random token stored in a table passes, but costs a write per email. A **signed** token passes
every point.

## 2. Hash vs HMAC vs encryption vs signature ⭐⭐⭐

| Tool | Key? | Gives you | Masternova example |
|---|---|---|---|
| hash (SHA-256) | none | a fingerprint; **anyone** can compute it | storing verification and refresh tokens **hashed** (identity) |
| **HMAC** (HMAC-SHA256) | one **shared secret** | integrity + authenticity: only key holders can make or check the tag | unsubscribe tokens; JWT HS256 access tokens; Razorpay webhook signatures (9.4) |
| encryption (AES-GCM) | secret | **confidentiality**: nobody can read the content | not needed: the payload (user id, category) isn't secret |
| digital signature (RS256, Ed25519) | private + public key pair | authenticity that **others can verify without being able to sign** | Sigstore/cosign image signing (D2) |

⭐ **An HMAC token is signed, not encrypted.** Its payload is readable base64. Never put a secret
inside it.

Use HMAC when **the same party (or parties sharing one secret) signs and verifies**: here, the
worker and the api. Use a key pair when **outsiders** must verify.

## 3. Why not `sha256(secret + message)`? ⭐⭐

SHA-256 is a Merkle–Damgård hash. Given `sha256(secret + msg)` and the length of the secret, an
attacker can compute `sha256(secret + msg + padding + extra)` **without knowing the secret**.
That's a *length-extension attack*. HMAC's construction, `H(k ⊕ opad ‖ H(k ⊕ ipad ‖ m))`, is
immune. **Always use `Mac`, never hand-rolled "secret prefix" hashing.**

## 4. The format ⭐⭐

```text
base64url(userId "." category "." expiresEpochSec) "." base64url(HMAC-SHA256(secret, part1))
└──────────────── payload (readable) ─────────────┘     └──────────── tag (32 bytes) ────────────┘
```

- **base64url without padding:** only `A–Z a–z 0–9 - _`, so the token goes into a URL with no
  escaping (`Base64.getUrlEncoder().withoutPadding()`).
- **Sign the encoded payload string**, exactly the bytes you will receive back. That is what JWT
  does too: no ambiguity about canonical forms.
- **Expiry inside the signed part:** the client can't extend it, because changing it breaks the tag.
- **One `.` separates the parts.** The verifier rejects any other count before doing any work.

## 5. The code ⭐⭐⭐

```java
private byte[] sign(String payload) {
  Mac mac = Mac.getInstance("HmacSHA256");      // ⭐ a new Mac per call: Mac is NOT thread-safe
  mac.init(key);                                 // key = new SecretKeySpec(secretBytes, "HmacSHA256")
  return mac.doFinal(payload.getBytes(UTF_8));
}

public Optional<Unsubscribe> verify(String token, Instant now) {
  …split at the single '.'…
  byte[] signature = B64_DECODE.decode(token.substring(dot + 1));
  if (!MessageDigest.isEqual(sign(payload), signature)) {   // ⭐ constant-time comparison
    return Optional.empty();
  }
  …only now decode and parse the payload, then check expiry…
}
```

The four rules in that snippet:

1. ⭐⭐⭐ **Constant-time comparison.** `Arrays.equals` / `String.equals` return at the first
   differing byte. By timing many requests, an attacker learns how many leading bytes were right
   and forges a tag byte by byte. `MessageDigest.isEqual` always compares every byte.
2. ⭐⭐⭐ **Verify before you parse.** Never trust or even interpret payload bytes until the tag
   matches. A parser bug on attacker-controlled input is then unreachable.
3. ⭐⭐ **`Mac` is stateful and not thread-safe.** Either create one per call (`getInstance` is
   cheap: the provider lookup is cached) or keep a `ThreadLocal<Mac>`. A shared `Mac` field under
   virtual threads produces **wrong tags**, not exceptions.
4. ⭐⭐ **All failures look the same.** Forged, expired, malformed, wrong category: one empty
   `Optional`, one `422 UNSUBSCRIBE_TOKEN_INVALID`. Different messages would tell a forger which
   check they passed. The method also **never throws** on garbage input
   (`garbageNeverThrowsItIsJustInvalid`).

## 6. Keys ⭐⭐

- **Length ≥ the hash output (32 bytes for SHA-256).** The constructor refuses shorter secrets.
  `make secrets` generates 48 random bytes, base64-encoded.
- **A file secret, not an env var** (`/run/secrets/NOTIFICATION_TOKEN_SECRET`, read through
  `spring.config.import: optional:configtree:/run/secrets/`; DevOps note 03).
- **Both deployables share it:** the worker signs, the api verifies. If they differ, every link
  silently fails as "invalid", which is why the `.yaml` comments say *MUST equal*.
- **Rotation (⭐, not built yet):** put a key id in the token (`kid`, like JWT headers), accept the
  old and new key during a 30-day overlap, and sign with the new one only.

## 7. Stateless vs stored tokens ⭐⭐⭐

| | Signed (HMAC) token | Random token + DB row |
|---|---|---|
| storage | none | one row per token |
| revoke one token early | ❌ impossible (rotate the key = revoke **all**) | ✅ delete the row |
| single use | ❌ (unless you store "used") | ✅ |
| leaks if the DB leaks | nothing to leak | store a **hash**, not the token |
| fits | low-risk, idempotent, high-volume (unsubscribe, playback URLs) | high-risk, single-use (email verification, password reset, refresh tokens) |

That's why Masternova uses **both**. Email verification and refresh tokens are random + hashed
rows, because they must be single-use and revocable. Unsubscribe is HMAC, because it's
idempotent and harmless to repeat, and there are millions of links.

## 8. Pitfalls ⭐⭐

- **Comparing tags with `equals`:** a timing leak (rule 1).
- **Signing the decoded fields instead of the exact received string:** two encodings of the same
  data then verify differently, or worse, two different payloads share a tag.
- **Unbounded tokens:** no expiry means a leaked link works forever.
- **Mandatory categories:** the worker never issues tokens for them, but the api *also* refuses
  them (`filter(u -> !u.category().mandatory())`). Defence in depth costs one line.
- **A GET that changes state:** mail scanners prefetch links. The link opens a page; the page
  POSTs.

## 9. Try it in jshell ⭐

```bash
cd backend && ./mvnw -q -pl kernel compile && jshell --class-path kernel/target/classes
```

```java
import com.masternova.kernel.notification.*; import java.time.*; import java.util.*;
var t = new UnsubscribeTokens("a-secret-that-is-at-least-32-bytes-long!!");
var token = t.issue(UUID.randomUUID(), NotificationCategory.PRODUCT_NEWS, Instant.now().plusSeconds(60));
t.verify(token, Instant.now());                         // Optional[Unsubscribe[…]]
t.verify(token.replace('A', 'B'), Instant.now());       // Optional.empty — tampered
new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));   // ⭐ readable: signed ≠ encrypted
```

## 10. Interview Q&A

- **Q: What's the difference between a hash and an HMAC?**
  **A:** Anyone can compute a hash; only holders of the key can compute an HMAC. So an HMAC
  proves integrity **and** origin.
- **Q: Why `MessageDigest.isEqual` and not `Arrays.equals`?**
  **A:** It's constant-time. Early-exit comparison leaks, through response timing, how much of a
  forged tag is correct.
- **Q: JWT HS256 vs RS256?**
  **A:** Both sign the header and payload. HS256 is HMAC with one shared secret: everyone who
  verifies can also forge. RS256 uses a key pair: verifiers hold only the public key. Use RS256
  when third parties verify your tokens.
- **Q: How would you revoke one signed token?**
  **A:** You can't without state. Add a denylist (with a TTL equal to the token's), or shorten
  the expiry, or switch that use case to stored tokens.
- **Q: Is it safe to put the user id in a URL token?**
  **A:** Yes if it's signed and the id isn't secret: the signature stops tampering. Not if the
  content must be hidden; that needs encryption.

## 11. 30-second recall

- **HMAC = keyed hash:** integrity + authenticity with one shared secret. Signed ≠ encrypted.
- **Format:** `b64url(payload).b64url(HMAC(payload))`, with the expiry inside the payload.
- **Rules:** `MessageDigest.isEqual`; verify before parsing; a new `Mac` per call; one generic
  failure; key ≥ 32 bytes as a file secret.
- **Stateless vs stored:** HMAC for idempotent, high-volume links; random + hashed row for
  single-use or revocable ones.
- **In Masternova:** the worker issues, the api verifies (kernel `UnsubscribeTokens`). Later:
  playback tokens (8.5) and Razorpay webhooks (9.4).
