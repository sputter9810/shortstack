# ShortStack

ShortStack is a Java/Spring Boot URL-shortening REST API portfolio project.
It demonstrates API design, relational persistence and backend correctness.
V1 implements generated links, optional custom aliases and expiration, metadata,
redirects and hard deletion, backed by PostgreSQL and database-enforced uniqueness.
Set `SHORTSTACK_BASE_URL` to the external HTTP(S) origin (default
`http://localhost:8080`); response URLs never trust the request Host header.
See [PROJECT.md](PROJECT.md) for scope and [the API contract](docs/api-contract.md),
[domain rules](docs/domain-design.md) and [persistence design](docs/persistence-design.md)
for the architecture and engineering rationale.

## Prerequisites and versions

- JDK 21 LTS (set `JAVA_HOME` to the JDK installation).
- Spring Boot 4.1.2, with dependency/plugin versions managed by its Maven parent.
- Maven 3.9.16, downloaded by Maven Wrapper 3.3.4 on first use; no system Maven needed.
- Internet access for the initial Maven and dependency downloads.
- Docker Desktop (Linux containers) or Docker Engine, with Docker Compose supporting
  `up --wait`. The database image is PostgreSQL 17.

The base package is `io.github.sputter9810.shortstack`. The standard Maven layout
contains the application under `src/main/java`, configuration under
`src/main/resources`, and tests under `src/test/java`.

## Prepare local database configuration

From the repository root, copy the development-only examples (do not overwrite an
existing `.env`):

```powershell
Copy-Item .env.example .env
```

Edit `.env`, especially `DB_PASSWORD`. These are local demonstration values, not
credentials for a real/shared environment. Keep plain `KEY=value` lines without
quotes or inline comments for the loader below. The ignored `.env` stays local.
`DB_HOST=localhost` is the application's database host; `DB_PORT` is the host port
published by Compose. `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD` initialise the
database and login on the volume's first use.

Compose automatically reads `.env` for substitution into `compose.yaml`; existing
shell environment values take precedence. Spring Boot does **not** load `.env`.
Load the same five variables into the PowerShell session used for Maven/the JAR:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USERNAME|DB_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
```

This explicitly supplies process environment variables to Spring's `${DB_*}`
placeholders and Compose. There is no dotenv library or automatic configuration
import. In other shells, export the same five variables before running Maven/Java.
If changing the file in a loaded session, rerun the loader. Application/test startup
requires these variables and a reachable PostgreSQL database; none has a
committed credential fallback.

## Start PostgreSQL and check readiness

Ensure Docker is running, then:

```powershell
docker compose config --quiet
docker compose up -d --wait postgres
docker compose ps
docker compose exec postgres pg_isready -U $env:DB_USERNAME -d $env:DB_NAME
```

The service must be healthy and `pg_isready` must report accepting connections.
The health check confirms server readiness, not that Flyway has run or the
application password is correct. The port is published on loopback only. The
application runs on the host; the PostgreSQL container always listens on 5432.
If the host port is occupied, change `DB_PORT` and reload the environment.

## Build, test and run

After loading the environment and starting PostgreSQL, from the repository root
on Windows PowerShell:

```powershell
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `sh ./mvnw` instead of `.\mvnw.cmd`, for example
`sh ./mvnw clean verify`. The wrapper uses the official `only-script` distribution:
`mvnw`, `mvnw.cmd` and `.mvn/wrapper/maven-wrapper.properties` are all required;
no wrapper JAR is needed.

Alternatively, after building, run the executable JAR:

```powershell
java -jar target/shortstack-0.0.1-SNAPSHOT.jar
```

The web server uses port 8080 by default. Stop it with Ctrl+C. A `404` at `/` is
expected; business routes are listed below. Set `SHORTSTACK_BASE_URL` when the
external origin differs from the default, and `SERVER_PORT` to change the listener.

Verification uses the configured PostgreSQL database. Use a dedicated local
development database, never shared or production data. Tests cover unit rules,
HTTP validation/errors, repository mappings, migrations, committed transactions,
expiration boundaries, lifecycle behaviour and coordinated concurrency races.
Fixed clocks and bounded thread coordination avoid timing-dependent assertions.
Repository tests roll back; tests with independent commits clean only their own
destination markers, and migration tests drop only their isolated schema. The
login needs permission to create/drop its test schema (the local Compose login
supports this). Identity sequences may advance. No H2 or Testcontainers is used.

Focused tests can be run with `-Dtest`, for example:

```powershell
.\mvnw.cmd -Dtest=ShortCodeGeneratorTests test
.\mvnw.cmd -Dtest=ShortLinkRepositoryTests test
```

## API usage

The examples assume the application is running on `http://localhost:8080`.
PowerShell creates a generated link, inspects metadata, observes the redirect
without following it, and deletes the link:

```powershell
$link = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/links `
    -ContentType application/json -Body '{"destinationUrl":"https://example.org/articles?id=42#intro"}'
$link
Invoke-RestMethod -Uri "http://localhost:8080/api/links/$($link.code)"
curl.exe --include "http://localhost:8080/$($link.code)"
Invoke-WebRequest -UseBasicParsing -Method Delete -Uri "http://localhost:8080/api/links/$($link.code)"
```

Creation returns `201`, JSON metadata and a `Location` identifying the metadata
resource. `shortUrl` identifies the redirect route. Metadata includes `code`,
`shortUrl`, `destinationUrl`, `createdAt`, nullable `expiresAt` and derived `expired`;
it never includes the database ID. Representative JSON appears in the
[API contract](docs/api-contract.md).

Create an alias with future expiration (use a different alias if already present):

```powershell
$aliasBody = @{
    destinationUrl = 'https://example.org/launch'
    customAlias = 'launch-demo'
    expiresAt = [DateTime]::UtcNow.AddDays(1).ToString("yyyy-MM-dd'T'HH:mm:ss.ffffff'Z'")
} | ConvertTo-Json
$aliasLink = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/links `
    -ContentType application/json -Body $aliasBody
Invoke-WebRequest -UseBasicParsing -Method Delete -Uri "http://localhost:8080/api/links/$($aliasLink.code)"
```

| Operation | Success | Missing/deleted | Existing expired row |
|---|---|---|---|
| POST `/api/links` | `201`, metadata | N/A | Past/equal requested expiry rejected with `400` |
| GET `/api/links/{code}` | `200`, metadata | `404 LINK_NOT_FOUND` | `200`, `expired: true` |
| GET `/{code}` | Empty `302`, stored destination Location | `404`, no Location | `410 LINK_EXPIRED`, no Location |
| DELETE `/api/links/{code}` | Empty `204` | `404` (including repeated DELETE) | Empty `204` |

Every application response uses `Cache-Control: no-store`. Redirects preserve the
stored destination's query/fragment and ignore short-URL query parameters. Hard
deletion releases a code; an old short URL can eventually resolve to a new
destination after reuse. Expired but undeleted codes remain reserved.

Destination URLs must be absolute HTTP(S), 1–2048 ASCII characters, with a valid
host/optional port and no embedded user information, whitespace, controls or
backslashes. Supply international hosts in punycode and non-ASCII components
percent-encoded. Validation never fetches a destination and does not guarantee
safety or trustworthiness. Values are not trimmed or rewritten.

Aliases are case-sensitive, 3–32 ASCII letters/digits/underscores/hyphens. Reserved
names are rejected case-insensitively. A duplicate alias returns `409 ALIAS_CONFLICT`
without retry. Expiration is optional RFC 3339 with an explicit offset, at most six
fractional digits and a strictly future instant at creation validation. Responses
normalise timestamps to UTC with six fractional digits. A link is expired at
`now >= expiresAt`; no background deletion occurs.

Errors use `timestamp`, `status`, `error`, `message`, `path` (without query), and
`violations` (always an array). Invalid field values use `400 VALIDATION_FAILED`;
malformed JSON, unknown properties and wrong JSON field types use
`400 INVALID_REQUEST`. Exhausted generation and recognised database unavailability
use controlled `503`; unexpected failures use sanitised `500`. SQL, stack traces
and supplied values are not returned in errors. See the full
[failure table](docs/api-contract.md#structured-failures) for method/media/route cases.

## Architecture

`LinkController` and `RedirectController` handle HTTP and DTOs. `LinkService` owns
creation, alias policy, metadata, resolution, expiry and deletion, using an injected
UTC clock. `GeneratedLinkService` coordinates generated allocation through a
separate transactional inserter. `ShortLinkRepository` performs persistence, while
Flyway and PostgreSQL own the schema/invariants. Advice, a fallback error controller
and a no-store filter keep HTTP failures consistent. No entity is an API contract.

The internal model has only an identity ID, code, destination and two timestamps;
there is no active flag, owner, analytics or alias-provenance state. Read transactions
are short; deletion is a single affected-row-count write. No transaction spans HTTP
response delivery. [The persistence design](docs/persistence-design.md) and
[correctness review](docs/correctness-review.md) explain constraints and races.

Generated codes use eight Base62 characters from unbiased `SecureRandom.nextInt(62)`
draws. The database constraint `uq_short_link_code` arbitrates the shared generated
and alias namespace; there is no check-then-insert availability query.
`GeneratedLinkService` permits five total attempts and rejects an outer transaction
with `NEVER`. A separate proxied `GeneratedLinkInserter` flushes a fresh entity in
`REQUIRES_NEW`, completing commit or rollback before returning. Only the exact
code constraint plus SQLSTATE `23505` is treated as a collision. Generated requests
retry, then return `503 CODE_GENERATION_EXHAUSTED`; aliases return `409` immediately.
The [correctness review](docs/correctness-review.md) explains the guarantees and limits.

## Continuous integration

[The CI workflow](.github/workflows/ci.yml) runs on pull requests targeting `main`
and pushes to `main`. Its single build-and-test job uses `ubuntu-latest`, Eclipse
Temurin Java 21, Maven dependency caching and the committed executable Maven Wrapper:

```sh
./mvnw -B -ntp clean verify
```

GitHub Actions starts a health-checked PostgreSQL 17 service with a temporary
database and non-sensitive CI-only example credentials, independent of local `.env`
values. The job supplies all five `DB_*` environment variables. Existing tests,
Flyway V1 migrations and Hibernate validation run normally; compilation, migration
or test failures fail the job. The service and its data are discarded after the
job. CI does not deploy or publish images. Local verification still requires Java
21 and a configured PostgreSQL 17 database as described above.

## Verify migrations and schema

Flyway owns schema creation. Spring Boot runs
`src/main/resources/db/migration/V1__create_short_link.sql` before JPA initialisation.
On a new database, build/test or application logs should show migration to version
1 (`create short link`) succeeding. Verify with:

```powershell
docker compose exec postgres psql -U $env:DB_USERNAME -d $env:DB_NAME -c 'TABLE flyway_schema_history;'
docker compose exec postgres psql -U $env:DB_USERNAME -d $env:DB_NAME -c '\d+ short_link'
```

Expect one successful version-1 migration, the five approved columns, identity ID,
and the named constraints including `uq_short_link_code`. Subsequent startups
validate Flyway's history/checksums and report the schema up to date; V1 is not
executed again. Do not edit an applied migration; later approved schema changes
need a new migration. Flyway's own history table is migration metadata, not an
additional domain feature.

Hibernate uses `ddl-auto=validate`; automatic schema creation/updates and Spring
SQL-script initialisation are disabled. Open Session in View is disabled, and
PostgreSQL sessions/JDBC timestamp handling use UTC. The `persistence.ShortLink`
entity maps the existing five-column table, and context startup validates that
mapping against the Flyway-managed schema. Hibernate validation does not verify
every check constraint, collation or uniqueness rule; direct database inspection
and PostgreSQL integration checks remain necessary. `ShortLinkRepository` extends
Spring Data JPA's standard operations, exact `findByCode` lookup and an atomic
`deleteByCode` operation returning the affected-row count.

## Stop PostgreSQL and persistent data

```powershell
docker compose down
```

This removes the container/network but keeps the project-scoped named
`postgres_data` volume, including links and migration history. The next
`docker compose up -d --wait postgres` reuses it. Changing the initialisation
username/password/database in `.env` does not change an already-initialised volume.

**Destructive reset — deletes all data in this Compose project's database volume:**
only when intentionally discarding local data, stop the application, confirm the
current directory/project is ShortStack and run:

```powershell
docker compose down --volumes
docker compose up -d --wait postgres
```

Then start the application/build again to apply Flyway to the empty database.
Do not use global Docker volume pruning as a reset procedure.

## Configuration and V1 limits

Use Spring Boot's external configuration conventions: environment variables,
command-line arguments or external configuration files. For example, set
`SERVER_PORT=8081` in the environment, or pass `--server.port=8081` to the JAR.
The committed configuration externalises the database and contains no real credentials,
machine-specific paths or speculative deployment profiles. Local environment and
`application-local.*` files are ignored; Spring Boot does not automatically load
`.env` files. Do not commit secrets.

Dependencies include Spring MVC, Spring Data JPA/Hibernate, the PostgreSQL JDBC
driver, Spring Boot's Flyway starter, Flyway's PostgreSQL module and the test
starter. Versions are managed by Spring Boot's Maven parent.

V1 is intended for local development and portfolio demonstration. Its approved
unauthenticated API design is unsuitable for unrestricted public deployment
without additional security controls.

Coverage percentages and production throughput were not measured.

V1 excludes accounts, frontend, analytics, distributed caches, deployment and rate
limiting infrastructure. It does not promise exactly-once POST delivery or revoke
redirects already read before deletion. Licensing and final portfolio acceptance
remain owner decisions; no licence is silently selected by the implementation.

## Technical documentation

- [Project specification](PROJECT.md)
- [API contract and response examples](docs/api-contract.md)
- [Domain rules and allocation policy](docs/domain-design.md)
- [Persistence schema and transactions](docs/persistence-design.md)
- [Concurrency and correctness rationale](docs/correctness-review.md)
