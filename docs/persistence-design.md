# V1 persistence design and Issue #1 review

**Status: Spring Data JPA/Hibernate, Flyway, PostgreSQL, the schema and constraints,
transaction boundaries and collision policy are approved by Sam. Documentation
revisions await final verification; Issue #1 remains in Review, not Done.**
17 October 2026. No SQL below has been created as a
migration or executed. Read [API contract](api-contract.md) and
[domain rules](domain-design.md) as one design. Architectural approval does not
claim implementation, successful runtime tests or final acceptance of this revision.

## Persistence choices and boundaries

The owner approved Spring Data JPA/Hibernate for conventional entity/repository support,
PostgreSQL as the authoritative database, and Flyway versioned SQL migrations.
These refine the intentionally open persistence choices in `PROJECT.md`; they
do not replace its stack. JPA suits this small model and the portfolio's layered
Spring objective; explicit JDBC would expose SQL more directly but require more
mapping. Hibernate can hide flush timing, so transaction behaviour below is part
of the contract. Flyway makes a small PostgreSQL schema easy to review as SQL;
Liquibase is viable but its richer change model is unnecessary here. These are
approved future dependency choices, not additions in this issue. Versions and build
configuration belong to a later scaffolding issue after Issue #1's final acceptance.

Controllers handle DTO binding, request syntax, headers and status codes. They
delegate to services and never expose JPA entities. Services own destination/alias
rules, creation times, derived expiration, generation/retries and deletion policy.
Repositories perform exact-code queries/inserts/deletes without deciding status
codes or expiration. Central exception mapping translates typed failures into the
API envelope and logs internal diagnostics without returning them to clients.
Disable Open Session in View; materialise response data inside the service scope.

Use Java `Long` for the internal identity and `Instant` for both timestamps.
No updates are exposed, so no optimistic version field is needed in V1. Identity
BIGINT is compact and sufficient for a single PostgreSQL database. UUIDs permit
independent ID generation but add size without benefit here; a code primary key
would couple external naming policy to persistence identity. ID gaps after failed
transactions are expected. IDs are never client-facing or the short-code source.

Flyway will own schema creation/evolution; Hibernate schema mode should be
`validate`, never `create`, `update` or `create-drop` for the persistent application.
Use PostgreSQL for integration tests, including migration and constraint tests;
H2 is not a substitute for these semantics. Configure UTC for JDBC/database
sessions and serialisation; `timestamptz` represents an instant, not the original
user offset. Store microsecond precision as specified in the domain contract.

## Approved schema design (documentation only)

```sql
CREATE TABLE short_link (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    code VARCHAR(32) COLLATE "C" NOT NULL,
    destination_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_short_link PRIMARY KEY (id),
    CONSTRAINT uq_short_link_code UNIQUE (code),
    CONSTRAINT ck_short_link_code_format
        CHECK (code ~ '^[A-Za-z0-9_-]{3,32}$'),
    CONSTRAINT ck_short_link_reserved_code
        CHECK (lower(code) NOT IN (
            'api', 'error', 'actuator', 'health', 'metrics', 'docs',
            'swagger-ui', 'v3', 'favicon.ico', 'robots.txt'
        )),
    CONSTRAINT ck_short_link_destination_nonempty
        CHECK (char_length(destination_url) BETWEEN 1 AND 2048),
    CONSTRAINT ck_short_link_expiration
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT ck_short_link_created_range
        CHECK (created_at >= TIMESTAMPTZ '0001-01-01 00:00:00+00'
           AND created_at < TIMESTAMPTZ '10000-01-01 00:00:00+00'),
    CONSTRAINT ck_short_link_expiration_range
        CHECK (expires_at IS NULL OR (
            expires_at >= TIMESTAMPTZ '0001-01-01 00:00:00+00'
            AND expires_at < TIMESTAMPTZ '10000-01-01 00:00:00+00'
        ))
);
```

Only `expires_at` is optional. The service supplies created_at from its injected
clock; no independent database default competes with it. The expiry check compares
stored values, not moving `now()`; rows remain valid when time passes. The year
bounds also exclude PostgreSQL infinity values, keeping API serialisation defined.
Full URI validation belongs in the service; the database's nonempty/length check
is intentionally not a second URI parser. Generated length is a service rule
because the shared column also accepts aliases of different lengths.

`COLLATE "C"` gives exact case-sensitive code equality and predictable ASCII
range checks; do not use `citext`, lowercased unique expressions or a locale's
case-insensitive collation. The reserved-name check is case-insensitive, separately
from identity. The named nondeferrable unique constraint covers all rows, including
expired links, and both code sources. PostgreSQL creates B-tree indexes for the
primary key and unique constraint. No duplicate code index is needed. No destination,
created_at or expires_at index is justified: V1 only looks up/deletes by code and
does not list, search, deduplicate or scan expired rows. Add indexes only with a
future approved query that needs them.

The reservation policy is unchanged. `v3` is shorter than the three-character
minimum; `favicon.ico` and `robots.txt` contain dots excluded by the code regex.
These three entries are therefore redundant for database code validation, but
remain listed to document root-route ownership. Other reserved names satisfy the
grammar and need the explicit check. Keep the agreed regex and reservation list.
Hard deletion releases the shared unique value for either a new alias or a new
generated code; expiry alone removes no row and releases no reservation. No
tombstones, soft-deletion state or reservation table is part of this schema.

## Transactions and concurrent operations

Use ordinary PostgreSQL READ COMMITTED isolation. The database unique constraint,
not serializable isolation or an application lock, decides concurrent allocation.

The creation orchestrator has **no outer transaction**. Prefer a small
`TransactionTemplate` with `REQUIRES_NEW` around each insert/flush/commit attempt;
catch duplicate exceptions outside `execute`. The returned success must include
commit completion. This gives each attempt an independent transaction and fresh
entity state, without relying on Spring proxy self-invocation. An alternative is
a separate proxied transactional writer bean; an annotated private or self-called
method is insufficient. Never catch a uniqueness error inside the failed transaction
and continue writing in it. Recognise SQLSTATE `23505` plus constraint
`uq_short_link_code` through the persistence exception cause chain. Other failures
must propagate to controlled mapping. The exact five-attempt algorithm is owned by
[domain rules](domain-design.md#generated-collisions-and-concurrency).

Each metadata or resolution lookup uses a short read-only transaction. Load by
exact code, sample the clock and derive expiry, materialise the DTO/resolution
result, then return. No write is performed for a redirect. A delete uses one write
transaction and a single `DELETE ... WHERE code = ?`, checking affected row count:
one gives `204`, zero gives `404`. Do not implement deletion as a prior existence
check followed by an unchecked delete. Map failures after transaction completion.
Two concurrent deletes yield one success and one missing result if there is no
intervening recreation. No transaction extends across HTTP delivery. A create
cannot acknowledge a row that was rolled back; network loss after commit remains
ambiguous to the client. No pessimistic locking or distributed coordination is needed.

An atomic PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` returning the inserted
row is a valid alternative that avoids uniqueness exceptions, but requires custom
SQL beyond the approved conventional JPA insert. Do not combine retry-in-aborted-
transaction logic with either approach. Review this choice again only if actual
implementation exposes a material maintainability problem.

## Verification plan for later implementation

These are future behavioural checks, not test implementations or claims of passing
runtime verification:

- API: generated/custom creation and Location, each metadata field, empty redirect
  and deletion responses, all structured failures, unsupported schemes, header
  injection/control characters, wrong JSON types and URL/alias length boundaries.
- Time: null expiry, offsets normalised to UTC, invalid local/overprecision input,
  equal/past creation expiry, one microsecond before/at/after expiry, expired
  metadata and deletion. Use a fixed injected clock.
- PostgreSQL: apply real Flyway migrations; duplicate inserts and case-distinct
  codes; shared alias/generated namespace; reserved names; time precision round
  trips, null expiry and schema checks; committed data visible to a fresh connection.
- Concurrency: coordinate separate transactions selecting the same candidate;
  assert one row and correct loser response/retry. Force four collisions then
  success, five collisions then 503, and unrelated failure with no collision retry.
  Confirm fresh transaction state after a duplicate and no orphan failed inserts.
- Deletion: expired/active/unknown and repeated deletes, alias reuse, concurrent
  deletes, and a resolution already read before deletion as allowed by the contract.

## Issue #1 acceptance assessment

The live issue was read on 17 October 2026. PASS here means documentation supports
the criterion, not that functionality is implemented. The owner's revision request
records architectural approval; revised wording and final issue acceptance remain
pending. The original issue requirements are retained without architectural changes.

| Exact issue criterion | Assessment | Evidence |
|---|---|---|
| All listed architectural decisions have an explicit documented outcome. | PASS | API Operations/representations/failures; domain codes/time; persistence schema/transactions |
| The resulting API contract satisfies the approved V1 scope in PROJECT.md. | PASS | API four operations; domain validation, aliases and expiry; no extra business operations |
| The schema design enforces short-code uniqueness in PostgreSQL. | PASS (design) | Approved schema design `uq_short_link_code`, shared case-sensitive column |
| Collision handling has defined behaviour under concurrent requests. | PASS (design) | Domain allocation steps; independent transactions above |
| Expiration and deletion semantics are unambiguous. | PASS | Domain time/state rules; API operation matrix and examples |
| No Version 1 scope expansion has been introduced. | PASS | Same stack/domain; no frontend, accounts, analytics, caching or infrastructure expansion |
| Sam has reviewed and approved the decisions. | PARTIAL | Listed architectural decisions approved in the owner's revision request; retained API details and revised documentation await final verification (see Review handover) |
| Documentation is sufficient for later issues to proceed without undocumented conversation context. | PASS (documentation); final acceptance prerequisite | Linked API/domain/schema, validation rules, algorithm and verification plan |

Scope traceability to `PROJECT.md` Functional Requirements: 1/8 creation and DTOs;
2–4 generation/uniqueness/retries; 5–7 aliases and validation; 9/10 redirect/metadata;
11/12 time/expiry; 13 deletion; 14 structured errors; 15 PostgreSQL persistence;
16 schema invariants; 17 the complete HTTP contract. The Technical Requirements'
layering, migrations and future PostgreSQL tests are addressed above. Runtime
testing, Docker/CI and repository presentation remain later issues as planned.

## Review handover and source documents

No direct contradiction with `PROJECT.md` was found and no change to it is proposed.
No missing referenced standard: the Portfolio Repository Standard, AI Development
Guide and Definition of Done were read from the sibling `Personal/Portfolio-Standards`
repository. They are external governance documents, not copied into this repository.
The clean starting repository contained only PROJECT.md and no AGENTS.md was found
in its applicable directory ancestry. Do not infer that the project's full Definition
of Done is satisfied: no build/test/CI infrastructure exists here yet, and no human
acceptance of this revision or commit acceptance is claimed by this document.

The owner's final-revision request explicitly approves unauthenticated hard deletion
(`204`, unknown `404`), code reuse, the existing URL validation contract, generation
and alias rules, collision policy, UTC/Instant/microsecond expiration semantics,
expired metadata and `410` redirects, JPA/Hibernate, Flyway, PostgreSQL, schema and
constraints, and transaction boundaries. These approved decisions are preserved.

Approval of the complete issue is not inferred. The request requires the remaining
endpoint paths, response codes and DTO representations to stay unchanged. Retained
API details not individually enumerated in its approval list include the custom
error envelope, rejection of unknown JSON fields, configured base-URL policy and
`Cache-Control: no-store`. They remain documented unchanged and should be explicitly
confirmed during final verification; this revision does not independently mark
them newly approved. No technical contradiction requiring an architectural change
was found.

The approved demonstration boundary must remain visible: anyone with API access
and a code can inspect metadata or delete a row. Unrestricted public deployment
would require additional security controls outside V1. Reusable old short URLs can
resolve to new destinations, and valid destination syntax does not imply safety or
trustworthiness. These consequences are now explicit, without adding security
infrastructure or changing scope.

Sam should verify this documentation revision, confirm the retained API details,
and record final acceptance or revisions in Issue #1. Only after final acceptance
and applicable repository requirements may the owner mark Issue #1 Done and make
Issue #2 Ready. Issue #1 remains in Review; no commit, push, merge or implementation
is performed by this revision task.

Technical references supporting the proposal (not pinned dependency versions):

- [PostgreSQL unique indexes](https://www.postgresql.org/docs/18/indexes-unique.html): constraints create their own indexes.
- [PostgreSQL timestamps](https://www.postgresql.org/docs/current/datatype-datetime.html): timestamptz represents instants and displays in the session timezone.
- [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html): REQUIRES_NEW establishes independent physical transactions.
- [Spring transactional annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): proxy interception and self-invocation considerations.
- [HTTP semantics](https://www.rfc-editor.org/rfc/rfc9110.html): status and Location semantics used by the API.
