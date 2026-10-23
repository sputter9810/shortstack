# ShortStack

ShortStack is a Java/Spring Boot URL-shortening REST API portfolio project.
It demonstrates API design, relational persistence and backend correctness.
Currently only the application scaffold is implemented; no link endpoints exist.
See [PROJECT.md](PROJECT.md) for scope and [the API contract](docs/api-contract.md),
[domain rules](docs/domain-design.md) and [persistence design](docs/persistence-design.md)
for the architecture approved through Issue #1.

## Prerequisites and versions

- JDK 21 LTS (set `JAVA_HOME` to the JDK installation).
- Spring Boot 4.1.2, with dependency/plugin versions managed by its Maven parent.
- Maven 3.9.16, downloaded by Maven Wrapper 3.3.4 on first use; no system Maven needed.
- Internet access for the initial Maven and dependency downloads.

The base package is `io.github.sputter9810.shortstack`. The standard Maven layout
contains the application under `src/main/java`, configuration under
`src/main/resources`, and tests under `src/test/java`.

## Build, test and run

From the repository root on Windows PowerShell:

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
the Spring application context; it does not exercise future link functionality.

## Configuration and issue boundary

Use Spring Boot's external configuration conventions: environment variables,
command-line arguments or external configuration files. For example, set
`SERVER_PORT=8081` in the environment, or pass `--server.port=8081` to the JAR.
The committed configuration only names the application; it contains no credentials,
machine-specific paths or speculative deployment profiles. Local environment and
`application-local.*` files are ignored; Spring Boot does not automatically load
`.env` files. Do not commit secrets.

The scaffold depends only on Spring MVC (embedded HTTP server) and Spring Boot's
test starter. JPA/Hibernate, the PostgreSQL driver and Flyway are deliberately
deferred to Issue #3, which owns database configuration and migrations. This avoids
requiring a database, a datasource exclusion or a substitute database to start
Issue #2's scaffold. Docker Compose, CI and all business functionality remain
outside this issue.

V1 is intended for local development and portfolio demonstration. Its approved
unauthenticated API design is unsuitable for unrestricted public deployment
without additional security controls.
