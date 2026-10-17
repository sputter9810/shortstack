# V1 domain rules and code allocation

**Status: Code generation, alias rules, collision policy, URL validation,
expiration, hard deletion and reuse are approved by Sam. Documentation revisions
await final verification; Issue #1 remains in Review.** Issue #1,
17 October 2026. Companion documents: [API](api-contract.md),
[persistence and review](persistence-design.md). Approval records the architectural
decisions; it does not accept these documentation revisions or authorise starting
later issues in this task.

## ShortLink and destination validation

`ShortLink` contains only internal ID, code, destination URL, creation instant and
optional expiration instant. It is immutable after creation apart from hard
deletion. There is no active flag, owner, click count or alias-provenance field.

Accept a destination of 1–2048 ASCII characters that parses as an absolute,
hierarchical URI, has scheme HTTP or HTTPS (case-insensitive), a nonempty valid
host (DNS name, IPv4 or bracketed IPv6), no user-info, and an absent port or port
1–65535. Reject whitespace, control characters, backslashes, malformed percent
escapes, relative/opaque URLs and all other schemes. International host names
must be supplied in ASCII punycode and non-ASCII path/query/fragment characters
percent-encoded. Use URI parsing, not a single URL regex; define API tests for
host parsing with the selected Java version. Do not trim, rewrite, lowercase or
truncate the supplied destination. Preserve it for `Location` and metadata.

This is syntactic validation, not a guarantee that a destination is safe,
trustworthy or available. The project owner has approved these existing validation
rules and limits; validation is not an endorsement of the destination.
Do not fetch URLs or perform DNS/reachability checks. Localhost/private addresses
are permitted for local development; the backend never dereferences destinations.
The 2048-character bound is a deliberate portfolio limit rather than an HTTP limit.
Accepting arbitrary schemes or raw international text complicates safe redirects;
more permissive canonicalisation is an alternative requiring contract revision.
Duplicate destinations are allowed: uniqueness applies only to codes.

## Codes and custom aliases

| Property | Approved rule |
|---|---|
| Generated alphabet | `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789` (62 ASCII symbols) |
| Generated length | Exactly 8 characters |
| Generator | Java `SecureRandom`, independent uniform `nextInt(62)` for each position |
| Custom alias | Full match `[A-Za-z0-9_-]{3,32}` |
| Lookup code grammar | Same 3–32 character grammar, allowing both generated and custom codes |
| Matching | Case-sensitive exact ASCII identity; `Launch` and `launch` differ |
| Reserved names | `api`, `error`, `actuator`, `health`, `metrics`, `docs`, `swagger-ui`, `v3`, `favicon.ico`, `robots.txt` |
| Reservation comparison | Case-insensitive with locale-independent ASCII folding |
| Invalid/reserved alias | `400 VALIDATION_FAILED`, violation on `customAlias` |
| Duplicate alias | `409 ALIAS_CONFLICT`, no retry or suffix |
| Deleted-code reuse | Allowed for both aliases and generated codes |

Names containing a dot and `v3` already fail alias grammar, but are listed to make
route ownership explicit. Reserve root routes before the catch-all redirect;
reserved names never resolve as short links and return `404 ROUTE_NOT_FOUND`
unless a separately approved framework route owns them. This reservation does
not introduce actuator, documentation endpoints or health features. `/api` remains
the API namespace. Generated candidates also pass reservation checks; an excluded
candidate is redrawn before an insert attempt is counted.

Do not normalise aliases, trim whitespace or decode path values a second time.
After normal framework path decoding, a matched `{code}` violating grammar returns
`400 INVALID_CODE`; a structurally unmatched route returns `404 ROUTE_NOT_FOUND`.
Encoded slashes may be rejected by the HTTP server before dispatch. JSON null or
omitted alias requests generation; empty/blank aliases are invalid.

Generated and custom values occupy the same `short_link.code` column and unique
constraint. An expired row still occupies its code. An alias that matches an
existing generated code conflicts identically, and vice versa. Case-sensitive
codes preserve the full Base62 space, at the cost of transcription mistakes;
case-insensitive aliases would require a different shared generation scheme.
No aliases are privileged or private; without authentication anyone who knows a
code can retrieve its destination and metadata or delete it. This includes metadata
for expired rows. These unauthenticated operations are approved for a local-development
and portfolio demonstration; they expose destinations and allow deletion without
an ownership check. The design is unsuitable for unrestricted public deployment
without additional security controls. No security mechanism or deployment
restriction is implemented in this issue.

## Generated collisions and concurrency

The owner has approved **five total database insert attempts**, including the initial attempt
(at most four retries). For a validated creation request:

1. Fix `createdAt` from the injected clock and validate expiration as below.
2. If customAlias exists, attempt exactly that code once.
3. Otherwise generate a fresh 8-character code for each attempt; do not reuse a
   failed entity or ID. Do not use an existence query as proof of availability.
4. Insert and flush in an independent transaction; return success only after its
   commit. Catch the failure outside that transaction after rollback completes.
5. Only SQLSTATE `23505` attributable to named constraint `uq_short_link_code`
   is a code collision. For aliases, return `409`; for generated codes, retry
   until five attempts have collided, then return `503 CODE_GENERATION_EXHAUSTED`.
6. Do not retry unrelated constraint failures, primary-key failures or database
   outages as collisions. Map them through the API's controlled errors.

Concurrent requests can choose the same code. PostgreSQL's unique constraint
arbitrates: only one can commit that code while its row exists. The loser may
wait for the winner's transaction, then receives a duplicate violation if the
winner commits, or may insert if the winner rolls back. Generated losers retry
in a new transaction; custom losers return conflict. At most one row per code
exists even for races between generated and custom requests. Deletion can free
a code before another insert; reuse after that deletion is intentionally allowed.

The space is `62^8 = 218,340,105,584,896` codes. For 100,000 occupied generated
values the next independent draw's collision chance is about `4.58e-10` (additional
custom values increase occupancy). This is not the probability that any historical
collision has occurred. Correctness comes from PostgreSQL and retries, not this
estimate. SecureRandom avoids predictable counters and biased modulo reduction;
codes are not security credentials. Five attempts bound work, not database wait
time. Configure ordinary connection/statement timeouts during later infrastructure
work; no busy loop, distributed lock, Redis or extra infrastructure is needed.

Counter/Base62 encodings remove random collision retries but expose sequence and
couple codes to ID allocation. Hashing destinations creates duplicate/collision
policy complexity. Random 8-character codes are simple and adequate here; UUID
strings would sacrifice shortness. Retry exhaustion has no guaranteed recovery
time, so omit `Retry-After`. A response lost after commit can leave a created row;
V1 does not promise exactly-once POST behaviour.

## Time, expiration and deletion

Accept RFC 3339 timestamps with an explicit `Z` or numeric offset, with at most
six fractional decimal digits. Reject local timestamps, leap-second input and
precision beyond microseconds with `400 VALIDATION_FAILED`; do not round user
input. Normalise to UTC. Use Java `Instant`, an injected `Clock`, and UTC output
with six fractional digits. Truncate clock samples to microseconds before using
them so application values and PostgreSQL `timestamptz(6)` agree.

Expiration is optional; null means no expiration. At creation validation sample
one instant `t0`; persist it as `createdAt`. A supplied `expiresAt` must be strictly
greater than `t0`. Reuse these values across collision attempts. A link may expire
during insertion/retries; that does not invalidate an otherwise valid creation.
No arbitrary maximum lifetime is imposed within supported Instant/database range;
V1 accepts four-digit years 0001–9999 to bound the interoperable timestamp format.

For each metadata/resolution operation, sample the service clock once after loading
the row. The link is expired exactly when `expiresAt != null && now >= expiresAt`.
At one microsecond before expiry it is active; at equality it is expired. Do not
rely on JVM local timezone, database session timezone or a background cleanup job.
Expired rows remain until explicitly deleted. Keeping metadata (`200`, expired
true) while returning redirect `410` makes the retained state inspectable.

Hard deletion removes the row and releases the code. It returns `204` for either
an active or expired row; deleting an absent row returns `404`. After deletion,
deleted and never-existing links are indistinguishable (`404` on either GET).
Expired links differ: metadata `200`, redirect `410`. Reuse may make an old short
URL resolve to a new destination; the owner has explicitly approved this consequence
for both aliases and generated codes. Expired but undeleted rows continue to reserve
their codes. Tombstones and soft deletion were considered as alternatives and are
excluded from the approved V1 design; no separate reservation table is introduced.

A resolution that reads the row before a concurrent deletion commits may finish
with `302` or `410`; already issued redirects cannot be revoked. A fresh lookup
after a committed deletion returns `404` unless a new row has reused the code.
Expiration is evaluated at the documented sample, not the time the client follows
the redirect. No locks spanning HTTP response delivery are proposed.

## Compatibility and approval

These choices refine `PROJECT.md` Functional Requirements 2–7 and 11–16, Data Model
Direction and Technical Requirements. Optional expiration, custom aliases and
hard deletion stay within scope. No unresolved contradiction was found. The owner
has approved the generation and alias rules, shared case-sensitive uniqueness,
URL validation limits, bounded independent-transaction retries, expiration rules,
accessible expired metadata, unauthenticated deletion, and code reuse. The
documentation revision and complete Issue #1 still await final human verification;
see the persistence document's review handover for approval scope.
