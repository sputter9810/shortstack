**Destructive reset - deletes all data in this Compose project's database volume:**

ShortStack is a Java/Spring Boot URL-shortening REST API portfolio project.
It demonstrates API design, relational persistence and backend correctness.
The application scaffold and database infrastructure are implemented; no link endpoints exist.
See [PROJECT.md](PROJECT.md) for scope and [the API contract](docs/api-contract.md),
[domain rules](docs/domain-design.md) and [persistence design](docs/persistence-design.md)
for the architecture approved through Issue #1.

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
now requires these variables and a reachable PostgreSQL database; none has a
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
.\mvnw.cmd clean verify
.\mvnw.cmd test
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
expected because there are no business controllers yet. The baseline test loads
the Spring application context, connects to PostgreSQL and runs Flyway. It does
not exercise future link functionality. These tests currently use the configured
local database, so use a dedicated development database, never shared/production data.

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
PostgreSQL sessions/JDBC timestamp handling use UTC. Before Issue #4 adds a JPA
entity, Hibernate has no `ShortLink` mapping to compare against the table. Its
successful startup is therefore **not** proof of table/constraint correctness;
inspect the database directly and retain PostgreSQL integration checks. This issue
does not create an entity or repository to work around that limitation.

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

## Configuration and issue boundary

Use Spring Boot's external configuration conventions: environment variables,
command-line arguments or external configuration files. For example, set
`SERVER_PORT=8081` in the environment, or pass `--server.port=8081` to the JAR.
The committed configuration externalises the database and contains no real credentials,
machine-specific paths or speculative deployment profiles. Local environment and
`application-local.*` files are ignored; Spring Boot does not automatically load
`.env` files. Do not commit secrets.

Dependencies now include Spring MVC, Spring Data JPA/Hibernate, the PostgreSQL JDBC
driver, Spring Boot's Flyway starter plus Flyway's PostgreSQL module, and the test
starter. All dependency versions remain managed by Spring Boot 4.1.2. No H2,
database auto-configuration exclusions or business classes are introduced. CI and
all business functionality remain outside Issue #3.

V1 is intended for local development and portfolio demonstration. Its approved
unauthenticated API design is unsuitable for unrestricted public deployment
without additional security controls.
