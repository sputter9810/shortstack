# Autonomous V1 execution ledger

Started 25 November 2026. This is implementation evidence, not human acceptance.
No issue will be closed, marked Done or merged by the agent.

## Starting assessment and source conflicts

- Repository/identity: `sputter9810/shortstack`, Sam Briggs; verified clean main at
  `3a87d26125551d3c3ccb10b0af8e4d5cfc0b433d`, equal to fetched origin/main.
- Issues 1–6 closed and merged; 7–17 open/Backlog. Issue 6 is present on main.
- `ISSUES.yaml` is absent. Live issue descriptions/labels/criteria were retrieved
  instead; no manifest is fabricated. The approved design documents retain
  historical pending-review wording despite Issue 1 being closed; the live closed
  issue and the owner's subsequent instructions establish approval. Contracts remain unchanged.
- Existing Java 21 target, Spring Boot 4.1.2, Maven 3.9.16, PostgreSQL 17,
  Flyway V1, Hibernate validate, REQUIRES_NEW insertion and NEVER coordinator retained.
- System Java is 24; verification uses a portable Java 21 outside the repository.
- Docker Desktop/local PostgreSQL started for verification. No production data used.
- Portfolio standards read: Repository Standard, AI Development Guide, Definition
  of Done, issue/prompt/project templates and standards README.

## Dependency-aware execution plan

Work follows issue order in a linear branch stack. Each row's parent is the
previous implementation branch (7 starts from main); each PR targets that parent.
The live issue remains the authoritative full acceptance checklist.

| Issue / owner | Objective and expected changes | Dependencies | Tests / documentation / risks and human gates | State |
|---|---|---|---|---|
| 7 creation / pair | POST DTOs, creation service, controller, configured base URL, clock | 1–6 | Unit + HTTP creation, persistence delegation, 201/Location/metadata; README; no API redesign | In Progress |
| 8 metadata / ai | Exact lookup, metadata DTO, missing 404 | 7 | Unit + HTTP found/missing/no redirect; README; expiry completed in 11 | Planned |
| 9 redirect / pair | Service resolution and GET redirect | 8 | Unit + HTTP 302/Location/missing; preserve query/fragment; expiry completed in 11 | Planned |
| 10 aliases / pair | Optional alias, syntax/reserved rules, one insert, conflict | 7–9 | Unit + HTTP generated/custom/invalid/conflict; shared DB uniqueness; no retry of aliases | Planned |
| 11 expiration / pair | Optional timestamp, fixed clock sample, derived expiry | 7–10 | UTC/microsecond input + boundary tests; 410 redirect, 200 metadata; no cleanup job | Planned |
| 12 deletion / pair | Atomic delete count, DELETE endpoint | 8–11 | Unit + HTTP absent/repeated/expired/reuse; no soft delete | Planned |
| 13 validation/errors / pair | Complete URI/JSON/code/time validation and central error envelope | 7–12 | Failure matrix + headers/media/framework cases; sanitised failures; approved rules only | Planned |
| 14 integration / pair | Extend existing PostgreSQL tests across complete operations | 7–13 | Real Flyway/constraints/timestamps/commits; cleanup only owned rows; no new dependency | Planned |
| 15 correctness / pair | Concurrency review + deterministic regression tests | 7–14 | Coordinated races, transaction/deletion/expiry review; Sam explanation gate remains pending | Planned |
| 16 documentation / pair | Accurate README, API examples, setup/testing/limitations | 7–15 | Execute examples and clean configuration; Sam reviews explanations | Planned |
| 17 final review / sam | Evidence, retrospective draft, acceptance checklist | 1–16 | Full build/startup/E2E; human code review, licence, roadmap and final approval cannot be invented | Human acceptance required |

## Material routine decisions

- Reuse Issue 6 generation/transaction code. HTTP DTOs never expose entities.
- Use an injected UTC Clock and externally configured absolute base URL, never Host headers.
- Reuse existing PostgreSQL/environment and JUnit/Mockito; no H2/Testcontainers.
- Stacked PRs targeting feature branches may have **no CI run** under the existing
  main-only trigger. This is not a pass; each PR must be retargeted to main and
  receive required hosted checks before human merge.
- Issue 17 requests a portfolio-roadmap edit outside this repository, while the
  experiment forbids unrelated repository edits. Prepare the outcome for Sam;
  do not edit that external repository or claim the project complete.

## Per-issue results and stack

Implementation entries, verification counts, commit/PR references and limitations
are appended as each deliverable becomes reviewable. Final acceptance remains human-owned.

### Issue 7 — link creation

- Branch `feature/issue-7-link-creation`, parent `main`; baseline 34/34 tests passed.
- Creation request/metadata records, controller, service mapping and injected UTC clock.
  Configured origin produces Location/shortUrl; Issue 6 coordinator remains untouched.
- Full Java 21/PostgreSQL verification: 36 passed, zero failed/skipped, including
  creation service and HTTP tests. Existing transaction/repository tests pass.
- All seven issue acceptance criteria supported by implementation/tests. Complete
  error validation, aliases and expiration remain intentionally assigned to 10/11/13.
- No schema/dependency/security change. Prepared for Review, never final acceptance.

### Issue 8 — metadata
- Parent feature/issue-7-link-creation (5f8f27a), PR #24; hosted CI passed (run 45604369459).
- Exact repository lookup, DTO-only GET, read-only transaction and structured missing 404. No redirects.
- Full verification: 38 passed, zero failed/skipped; found/missing HTTP and service paths covered. All six criteria supported.
- Branch feature/issue-8-metadata; expiry completion deferred to 11. No schema/dependency changes.

### Issue 9 — redirects
- Parent feature/issue-8-metadata at a4ece20, PR #25; parent PR targets feature-7 (no hosted run expected).
- Public GET delegates to service; 302 uses stored destination including query/fragment and ignores incoming query.
- Full verification: 40 passed, zero failed/skipped. Redirect/missing paths covered; all six criteria supported.
- Corrected trailing blank lines accidentally included in issue-8 documentation; no history rewrite.

### Issue 10 — aliases
- Parent feature/issue-9-redirect at faa64aa, PR #26.
- Optional alias uses one REQUIRES_NEW insert, exact shared uniqueness evidence, 409 on collision, 400 for syntax/reserved failures. No generated retries for aliases.
- Made the unchanged Issue-6 classifier package-visible to reuse its evidence checks; no algorithm change.
- Full verification: 51 passed, zero failed/skipped. Includes HTTP alias matrix and latch-coordinated real PostgreSQL race (one creation, one conflict); owned rows cleaned.
- All six acceptance criteria supported. Branch feature/issue-10-custom-aliases.

### Issue 11 — expiration
- Parent feature/issue-10-custom-aliases at 6d60746, PR #27.
- Strict future RFC3339 timestamps, UTC microseconds, central inclusive expiry; redirect 410 and metadata 200.
- Java21/PostgreSQL clean verification: 62 passed, zero failed/skipped; all seven criteria supported.
- Publication initially rejected by automatic review; explicit attachment authorization and matching origin were rechecked before retry.

### Issue 12 — hard deletion
- Parent feature/issue-11-expiration at 2bd1676, PR #28.
- Single atomic JPQL delete returns affected count in one write transaction; DELETE returns empty 204 or structured 404.
- PostgreSQL integration proves expired-row deletion, missing metadata/redirect/repeated delete and alias reuse.
- Full verification: 65 passed, zero failed/skipped; all six criteria supported. No soft-delete or schema changes.
