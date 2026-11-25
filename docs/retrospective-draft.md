# ShortStack retrospective draft

Prepared by the implementation agent for Issue 17, 25 November 2026.
**Evidence-based draft for Sam's review, not a claim of owner reflection or acceptance.**

## What worked

The small domain exposed meaningful backend concerns without extra infrastructure.
The approved schema, API and Issue 6 transaction boundary gave subsequent work
stable constraints. Per-issue branches and full verification preserved traceability;
the final stack has independently reviewable changes rather than one large diff.
Real PostgreSQL tests proved uniqueness, committed retries, timestamp precision,
schema migration and deletion behaviour. Fixed clocks and coordinated threads
made boundaries and races testable without arbitrary sleeps.

## Findings and corrections

- The original Java timestamp parser accepted a narrower offset range than the
  promised RFC 3339 contract. Explicit offset parsing and regression cases corrected
  that mismatch while preserving domain decisions.
- Main-only CI runs on the first PR but not PRs targeting feature parents. Local
  success is separate from hosted verification; the handoff requires retargeting
  and fresh checks rather than silently broadening triggers.
- Historical approval labels in design documents were stale after Issue 1 merged.
  Status prose was clarified without changing the approved contract.
- A documentation whitespace oversight was corrected in a later commit, retaining
  honest history rather than rewriting a shared branch.
- A missing ISSUES.yaml was recorded; live issue contents were used instead of
  fabricating authoritative inputs.

## Limits and owner discussion

The service intentionally lacks accounts, authentication, rate limiting, analytics
and public deployment controls. Anyone with API access and a code can inspect or
delete a link. Deleted-code reuse can redirect an old URL to a new destination.
These are approved local-demo trade-offs, not production security guarantees.
Tests do not measure coverage percentage, throughput or security assurance.

Sam should discuss which code he can explain confidently, whether the layered
boundaries remain easy to maintain, and whether licence/presentation choices meet
the portfolio goal. Final human review and the roadmap outcome remain pending.
