# V1 persistence design

Spring Data JPA/Hibernate maps the PostgreSQL schema managed by Flyway.
The SQL excerpt below matches `V1__create_short_link.sql`; it is documentation,
not a second migration. Read the [API contract](api-contract.md) and
[domain rules](domain-design.md) as one design.

## Persistence choices and boundaries

Spring Data JPA/Hibernate provides conventional entity/repository support,
PostgreSQL is the authoritative database, and Flyway owns versioned SQL migrations.
These refine the intentionally open persistence choices in `PROJECT.md`; they
do not replace its stack. JPA suits this small model and the portfolio's layered
Spring objective; explicit JDBC would expose SQL more directly but require more
mapping. Hibernate can hide flush timing, so transaction behaviour below is part
of the contract. Flyway makes a small PostgreSQL schema easy to review as SQL;
Liquibase is viable but its richer change model is unnecessary here. Dependency
and plugin versions are managed by the Spring Boot Maven parent.

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

Flyway owns schema creation/evolution; Hibernate uses `validate`, never `create`,
`update` or `create-drop` for the persistent application.
Use PostgreSQL for integration tests, including migration and constraint tests;
H2 is not a substitute for these semantics. Configure UTC for JDBC/database
sessions and serialisation; `timestamptz` represents an instant, not the original
user offset. Store microsecond precision as specified in the domain contract.

## Schema

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

The creation orchestrator has **no outer transaction**. `GeneratedLinkService`
uses `NEVER` to reject an ambient transaction. Each insertion is delegated to a
separate Spring-proxied `GeneratedLinkInserter` using `REQUIRES_NEW` and
`saveAndFlush`. The proxy completes commit or rollback before the caller receives
the result or exception. Each attempt creates fresh entity state; a private or
self-called transactional method would not establish this proxy boundary.

Failures are classified outside the failed transaction. Only a translated
integrity failure carrying Hibernate constraint `uq_short_link_code` and JDBC
SQLSTATE `23505` is a code collision. Cause traversal handles nested wrappers and
cycles. Other failures propagate to controlled API mapping. The exact five-attempt
algorithm is defined in [domain rules](domain-design.md#generated-collisions-and-concurrency).
Never continue writing in a transaction aborted by a uniqueness failure.

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

## Verification strategy

Automated tests exercise the following behaviour using real PostgreSQL and the
Flyway-managed schema:

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
- Deletion: expired/active/unknown and repeated deletes, alias reuse and concurrent
  deletes. The contract permits a resolution already read before deletion to finish;
  tests do not claim revocation of an in-flight response.

Hibernate validation checks mapped table/column compatibility; it does not prove
every constraint, collation or index. PostgreSQL integration tests inspect those
invariants directly. Fresh-schema migration tests apply V1 and verify subsequent
migration runs are no-ops. Fixture cleanup is scoped to owned records or schemas.

The unauthenticated demonstration API permits metadata access and deletion by
anyone who knows a code. Deleted codes can be reused, so old URLs may later
resolve elsewhere. Valid destination syntax establishes neither safety nor trust.
These limits do not require extra tables or security infrastructure in V1.

## Technical references

- [PostgreSQL unique indexes](https://www.postgresql.org/docs/17/indexes-unique.html): constraints create their own indexes.
- [PostgreSQL timestamps](https://www.postgresql.org/docs/17/datatype-datetime.html): timestamptz represents instants and displays in the session timezone.
- [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html): REQUIRES_NEW establishes independent physical transactions.
- [Spring transactional annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): proxy interception and self-invocation considerations.
- [HTTP semantics](https://www.rfc-editor.org/rfc/rfc9110.html): status and Location semantics used by the API.
