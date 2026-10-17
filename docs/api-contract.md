# V1 API contract

**Status: Architectural decisions listed in the owner's revision request are
approved by Sam. Documentation revisions await final verification; Issue #1
remains in Review until final human acceptance.** Prepared for
[Issue #1](https://github.com/sputter9810/shortstack/issues/1), 17 October 2026.
No application behaviour is implemented by this document. Read with
[domain rules](domain-design.md) and [persistence and review](persistence-design.md).
`PROJECT.md` remains authoritative. V1 uses `/api/links`, without a `/v1` prefix,
as outlined in its API Design Direction; version here denotes scope. Approval
scope and retained details requiring final verification are recorded in
[the review handover](persistence-design.md#review-handover-and-source-documents).

## Intended use and unauthenticated access

ShortStack V1 is a local-development and portfolio demonstration. The API has no
authentication or user accounts. Anyone with access to the API and knowledge of
a short code can retrieve its destination and metadata, including expired metadata,
or permanently delete the link. A short code is not an authorisation credential.
This design is unsuitable for unrestricted public deployment without additional
security controls. No authentication, API keys, authorisation or deployment
restrictions are introduced by this documentation revision.

## Operations

| Method and path | Success | Missing/deleted | Expired |
|---|---|---|---|
| `POST /api/links` | `201 Created`, metadata JSON | N/A | Reject past/equal expiration with `400` |
| `GET /api/links/{code}` | `200 OK`, metadata JSON | `404 Not Found` | `200 OK`, `expired: true` |
| `DELETE /api/links/{code}` | `204 No Content`, empty body | `404 Not Found` | Delete with `204` |
| `GET /{code}` | `302 Found`, destination in `Location`, empty body | `404 Not Found` | `410 Gone`, error JSON, no `Location` |

These four operations cover creation, metadata, resolution, aliases, expiration
and hard deletion. No listing, updating, analytics, accounts or UI is proposed.
Lookup and deletion match codes exactly; see domain validation for malformed paths.
All application responses use `Cache-Control: no-store`, including redirects and
errors, so clients and intermediaries are instructed not to cache stale redirects,
expiration responses or reusable deleted codes. In-flight requests can still finish.

## Creation and metadata representations

Request: `Content-Type: application/json`. `destinationUrl` is required;
`customAlias` and `expiresAt` may be omitted or JSON null. Empty strings are invalid.
Reject unknown properties and wrong JSON types with `400 INVALID_REQUEST` to catch
client mistakes. The body must be a JSON object. Validation precedes insertion;
multiple field violations may be returned together. Never truncate input silently.

```http
POST /api/links HTTP/1.1
Content-Type: application/json

{"destinationUrl":"https://example.org/articles?id=42#intro"}
```

Illustrative response (generated code varies):

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: http://localhost:8080/api/links/aB3xY9kQ
Cache-Control: no-store

{
  "code": "aB3xY9kQ",
  "shortUrl": "http://localhost:8080/aB3xY9kQ",
  "destinationUrl": "https://example.org/articles?id=42#intro",
  "createdAt": "2026-10-17T12:00:00.000000Z",
  "expiresAt": null,
  "expired": false
}
```

Custom alias with expiration:

```json
{
  "destinationUrl": "https://example.org/launch",
  "customAlias": "launch-2027",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Its `201` body has the same fields, `code: "launch-2027"`, corresponding
`shortUrl` and `Location`, and `expiresAt: "2027-01-01T00:00:00.000000Z"`.
`Location` identifies the metadata resource; `shortUrl` identifies the redirect.
Use one externally configured absolute HTTP(S) base URL with no path, query or
fragment (local default illustrated above). Do not derive it from untrusted Host
or forwarded headers. Deployment is outside V1; configuration remains external.
Database IDs and alias provenance are not exposed. Null expiration is always
included. There is no idempotency key: repeated POSTs may create different links
for the same destination, or return `409` for a repeated custom alias.

`GET /api/links/aB3xY9kQ` returns `200` and the same metadata representation.
For `GET /api/links/launch-2027`, evaluated on 1 January 2027 at the expiration
instant, return `200`:

```json
{
  "code": "launch-2027",
  "shortUrl": "http://localhost:8080/launch-2027",
  "destinationUrl": "https://example.org/launch",
  "createdAt": "2026-10-17T12:00:00.000000Z",
  "expiresAt": "2027-01-01T00:00:00.000000Z",
  "expired": true
}
```

`expired` is derived using the service clock for that operation, never stored.
Creation computes it at its validation instant (false); a link can expire before
the response reaches the client. Metadata remains available for expired rows to
explain their state; `410` applies only to attempted resolution.

## Redirect and deletion examples

```http
GET /aB3xY9kQ HTTP/1.1

HTTP/1.1 302 Found
Location: https://example.org/articles?id=42#intro
Cache-Control: no-store
```

Use the validated stored destination verbatim, including query and fragment.
Query parameters on the short URL are ignored, never appended to the destination.
`302` expresses a temporary resolution subject to expiration/deletion; permanent
`301`/`308` responses invite stale reuse. `307` is unnecessary for this GET contract.

```http
DELETE /api/links/launch-2027 HTTP/1.1

HTTP/1.1 204 No Content
Cache-Control: no-store
```

No request body or success response body is required for GET or DELETE. Repeating
that DELETE returns `404 LINK_NOT_FOUND`; the operation's effect is idempotent
even though statuses differ. Subsequent GETs also return `404`, until code reuse.
Hard deletion removes the row. Both deleted custom aliases and generated codes
may be reused, so an old short URL may eventually redirect to a different
destination. Expired but undeleted codes remain reserved. Until reuse, deleted
and never-existing codes are indistinguishable. These deletion and reuse semantics
have been explicitly approved by the project owner.

## Structured failures

All application-generated errors, including redirect failures, use
`Content-Type: application/json` and this fixed envelope:

```json
{
  "timestamp": "2027-01-01T00:00:00.000000Z",
  "status": 410,
  "error": "LINK_EXPIRED",
  "message": "The link has expired.",
  "path": "/launch-2027",
  "violations": []
}
```

`error` is a stable machine code; `message` is human-readable and not a parsing
contract. `path` excludes the query string. `violations` is always an array, with
`field` and `message` only; do not echo destination values, credentials, SQL,
constraint names, exception classes or stack traces. No error has `Location`.

| Status | `error` | Condition / representative message |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid URL, alias or expiration; "Request validation failed." |
| 400 | `INVALID_REQUEST` | Malformed JSON, unknown fields, wrong types; "The request body is invalid." |
| 400 | `INVALID_CODE` | Matched code parameter violates lookup grammar; "The code is invalid." |
| 404 | `LINK_NOT_FOUND` | Valid code has no row; "The link was not found." |
| 404 | `ROUTE_NOT_FOUND` | Unmapped route, including reserved root names |
| 409 | `ALIAS_CONFLICT` | Requested code already exists; "The custom alias is already in use." |
| 410 | `LINK_EXPIRED` | Existing expired row requested for redirect |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported method; include applicable `Allow` header |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | POST body is not JSON |
| 406 | `NOT_ACCEPTABLE` | Requested response media type excludes JSON where a JSON body is needed |
| 503 | `CODE_GENERATION_EXHAUSTED` | Five generated insert attempts collided; "Unable to allocate a short code. Try again." |
| 503 | `SERVICE_UNAVAILABLE` | Recognised database connectivity/unavailability failure |
| 500 | `INTERNAL_ERROR` | Unexpected application/persistence failure; "An internal error occurred." |

For `GET /missing-code`, metadata lookup or deletion of that absent code,
return `404` with the corresponding request path:

```json
{
  "timestamp": "2026-10-17T12:00:00.000000Z",
  "status": 404,
  "error": "LINK_NOT_FOUND",
  "message": "The link was not found.",
  "path": "/missing-code",
  "violations": []
}
```

Repeating the custom-alias creation while `launch-2027` still exists returns `409`:

```json
{
  "timestamp": "2026-10-17T12:00:00.000000Z",
  "status": 409,
  "error": "ALIAS_CONFLICT",
  "message": "The custom alias is already in use.",
  "path": "/api/links",
  "violations": []
}
```

Example validation body for `{"destinationUrl":"ftp://example.org/file","customAlias":"a!"}`:

```json
{
  "timestamp": "2026-10-17T12:00:00.000000Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/links",
  "violations": [
    {"field": "destinationUrl", "message": "Must be an absolute HTTP or HTTPS URL with a host."},
    {"field": "customAlias", "message": "Must contain 3 to 32 ASCII letters, digits, underscores or hyphens."}
  ]
}
```

Transport/proxy failures before application dispatch cannot guarantee this JSON
envelope. Framework-generated errors inside the application should be centralised.
HEAD, if supplied automatically by Spring for GET, follows GET status/headers and
suppresses the body; OPTIONS may advertise supported methods. Neither adds a new
business operation. No POST redirect or mutation-by-GET is proposed.

## Decision rationale and compatibility

`201` plus resource Location follows creation semantics; `200` would obscure
creation. `404` hides any distinction between deleted and never-existing rows
because hard deletion keeps no tombstone. `410` communicates a retained expired
link's unavailable redirect. `204` avoids inventing a deletion DTO; returning `204`
also for unknown codes is a reasonable alternative, but `404` provides explicit
feedback. A compact custom error DTO avoids requiring a problem-type URI registry;
RFC 9457 Problem Details was an alternative considered during design; the existing
DTO representation is retained for final documentation verification.

All choices refine `PROJECT.md` Functional Requirements 1–17 and API Design
Direction. No recommended endpoint or status is changed; expired metadata is
explicitly distinguished from expired resolution. The owner's approved decisions
are recorded in the review handover; this revision does not change endpoint paths,
status codes or DTO representations, or claim final acceptance of Issue #1.
HTTP semantics reference: [RFC 9110, sections 10.2.2 and 15](https://www.rfc-editor.org/rfc/rfc9110.html).
