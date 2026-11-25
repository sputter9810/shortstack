# Concurrency and correctness review

Prepared for Issue 15, 25 November 2026. Implementation evidence awaits Sam's
review and explanation; this is not final acceptance.

## Database arbitration and transaction boundaries

PostgreSQL's nondeferrable `uq_short_link_code` constraint is the shared authority
for generated codes and aliases, including expired rows. There is no availability
check before insertion. GeneratedLinkService retains Issue 6's five attempts,
NEVER outer-transaction contract and exact Hibernate/JDBC constraint/SQLSTATE
classification. GeneratedLinkInserter remains a separate proxied Spring bean with
REQUIRES_NEW and saveAndFlush. Failures leave the insertion proxy after rollback;
success returns after commit. Fresh entities are created per insertion attempt.

`ConcurrencyReviewTests` coordinates two threads at the candidate-generation
boundary so both attempt the same candidate; exactly one retries with a distinct
candidate and two unique committed rows result. The mocked generator controls
candidate values, while actual Spring proxies, JDBC transactions and PostgreSQL
uniqueness remain real. The existing alias race produces one creation and one
conflict without retries. Barriers and bounded futures replace sleeps. Fixture
suffixes guarantee different retry candidates; no random-uniqueness assertion is used.

Additional tests prove rollback recovery, exact exhaustion, unrelated failure
propagation and rejection of an outer coordinator transaction. Alias conflicts
reuse the unchanged exact classifier and never enter the generated retry loop.
No distributed locks, caching, isolation changes or new schema were introduced.

## Expiration and deletion

One microsecond-aligned clock sample is used for creation and reused across retry
attempts. Each read samples after loading the row; expiry is inclusive at equality.
The boundary tests cover one microsecond before, equality and after, with no sleeps.
Creation may finish after its validated expiration instant, per the approved contract.

Deletion issues one atomic delete by code and interprets its affected-row count.
The coordinated delete race produces one 204-equivalent success and one missing
result. Expired rows can be deleted, and deleted codes can be reused. Existing
PostgreSQL tests verify missing reads and reuse after deletion.

A resolution that loaded a row before a concurrent delete may still return its
stored destination; the short read transaction does not lock across HTTP delivery.
No-store headers instruct clients not to retain redirects, but cannot revoke an
already issued redirect. This allowed interaction does not require stronger locking.
Fresh lookups after commit see absence unless reuse intervenes. No test claims
revocation of an in-flight response or exactly-once HTTP delivery.

## Defect found and regression evidence

The first expiration parser used Java OffsetDateTime, whose numeric offsets stop
at 18 hours. The approved contract promises RFC 3339 numeric offsets, whose grammar
allows hours 00–23 and minutes 00–59. The parser now validates those components and
converts the local date-time to an instant with arithmetic, without relaxing date,
future, leap-second, precision or year-range checks. Regression tests cover positive
and negative 23:59, invalid 24:00/00:60 and the RFC's lowercase t/z forms.
This fixes contract compliance; it does not change the architecture.
Reference: [RFC 3339 section 5.6](https://www.rfc-editor.org/rfc/rfc3339.html#section-5.6).

## Quality and limits

- Java 21 Maven clean verification includes all previous tests, PostgreSQL schema
  validation, HTTP integration, migration, unit and concurrency tests.
- Flyway V1, existing dependencies, package boundaries and CI triggers remain intact.
- Owned-record destination markers and isolated schema cleanup prevent deletion of
  unrelated development data. Independently committed test rows are cleaned in finally/AfterEach.
- URI validation is syntactic and never fetches destinations. Valid syntax does not
  establish trust. No authentication is added; the approved local demonstration
  security boundary remains explicit.
- Code review, git whitespace checks and existing compiler/test gates are
  proportionate here; no dependency was added solely for a nominal quality metric.
- Coverage percentages and load/throughput limits were not measured. This review
  makes no production-scale performance or security claim.

Sam still needs to review and explain the concurrency strategy before Issue 15's
human acceptance criterion is satisfied. All PRs also need applicable hosted checks
after being retargeted to main; absent checks on stacked branches are not passes.
