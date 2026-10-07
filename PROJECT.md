# Project Brief

## Project

**Name:** ShortStack

## Purpose

ShortStack is a focused REST API for creating, managing, and resolving
shortened URLs.

A client submits a destination URL and receives a unique short code.
Requests to that code resolve to the stored destination through an HTTP
redirect. The service also supports link metadata, optional expiration,
custom aliases, and controlled deletion or deactivation.

The application is intentionally backend-focused. Its purpose is not to
become a feature-rich commercial URL-shortening platform, but to
demonstrate production-style backend engineering through a small domain
with meaningful persistence, correctness, API-design, validation,
concurrency, and testing concerns.

## Portfolio Objective

ShortStack is intended to demonstrate backend software-engineering
ability using Java, Spring Boot, and PostgreSQL.

The project should provide evidence of:

-   REST API and HTTP contract design;
-   layered Spring Boot architecture;
-   DTO-based API boundaries;
-   relational persistence with PostgreSQL;
-   explicit database schema migrations;
-   validation and structured error handling;
-   database-enforced constraints;
-   short-code generation and collision handling;
-   expiration semantics;
-   concurrency and correctness considerations;
-   unit, API, and integration testing;
-   containerised local infrastructure;
-   continuous integration;
-   configuration management;
-   professional backend documentation.

ShortStack is the second project developed under the portfolio's
AI-assisted development workflow. It should retain the successful
planning and review practices established during LogLens while placing
greater emphasis on human ownership of architectural and backend-design
decisions.

## Discipline

**Primary:** Backend

**Secondary:** Database Engineering; DevOps / Infrastructure

## Technology

**Primary language:** Java

**Application framework:** Spring Boot

**Database:** PostgreSQL

**Persistence:** A Spring-compatible persistence approach selected
during implementation, with PostgreSQL as the authoritative integration
database.

**Database migrations:** An explicit migration tool rather than
automatic production schema generation.

**Infrastructure/tooling:** Docker, GitHub, GitHub Actions, and a
standard Java build tool such as Maven.

Additional libraries should only be introduced where they provide clear
value and are proportionate to the project.

## Scope

### Included

Version 1 will include:

-   creation of shortened links from valid destination URLs;
-   automatic generation of unique short codes;
-   resolution of short codes to destination URLs;
-   HTTP redirect behaviour;
-   retrieval of stored link metadata;
-   deletion or deactivation of links;
-   optional expiration timestamps;
-   defined behaviour for expired links;
-   optional user-supplied custom aliases;
-   generated-code collision handling;
-   custom-alias collision handling;
-   destination URL validation;
-   PostgreSQL persistence;
-   database-enforced uniqueness for short codes;
-   explicit database migrations;
-   structured API error responses;
-   automated unit tests;
-   HTTP/API tests;
-   PostgreSQL-backed integration tests;
-   Docker-based local database setup;
-   continuous integration;
-   professional README and API usage documentation.

### Explicitly Excluded

Version 1 will not include:

-   a frontend or graphical user interface;
-   authentication or user accounts;
-   OAuth or JWT security;
-   an administrative dashboard;
-   QR-code generation;
-   email or billing functionality;
-   detailed click analytics;
-   Redis or distributed caching;
-   Kafka or other message brokers;
-   microservices;
-   Kubernetes;
-   complex rate-limiting infrastructure;
-   cloud infrastructure or deployment;
-   AI functionality.

These exclusions should only be reconsidered where a change provides
clear engineering value and is explicitly approved as a scope change.

## Functional Requirements

1.  ShortStack shall expose an HTTP API for creating a shortened link
    from a valid destination URL.
2.  When no custom alias is supplied, ShortStack shall generate a short
    code according to a documented generation strategy.
3.  Every persisted short code shall be unique.
4.  Generated-code collisions shall be handled through a defined
    retry/failure policy rather than uncontrolled errors or duplicate
    records.
5.  A client may optionally request a custom alias when creating a
    shortened link.
6.  A custom alias already in use shall be rejected with an appropriate
    HTTP response.
7.  Destination URLs shall be validated before a shortened link is
    created.
8.  Successful creation shall return useful metadata about the created
    link.
9.  ShortStack shall expose a route that resolves a valid short code and
    redirects the client to its destination URL.
10. ShortStack shall expose an API operation for retrieving metadata for
    an existing shortened link.
11. A shortened link may optionally define an expiration timestamp.
12. Expired links shall follow explicitly documented HTTP semantics and
    shall not redirect.
13. ShortStack shall provide a defined mechanism for deleting or
    deactivating an existing shortened link.
14. Missing, expired, invalid, or conflicting resources shall produce
    appropriate HTTP status codes and structured error information where
    applicable.
15. Link creation and resolution shall persist across application
    restarts through PostgreSQL.
16. Important database invariants, particularly short-code uniqueness,
    shall be enforced by the database as well as represented in
    application behaviour.
17. Exact endpoint paths, redirect status choice, expiration response
    semantics, deletion semantics, and response representations shall be
    finalised during API-design work and documented.

Functionality beyond these requirements is future work unless explicitly
approved as a scope change.

## Technical Requirements

-   Use supported modern Java and Spring Boot versions documented by the
    repository.
-   Use PostgreSQL as the primary application database.
-   Manage schema changes through explicit versioned database
    migrations.
-   Do not use automatic schema generation as the authoritative
    production schema-management strategy.
-   Use clear separation between HTTP/API handling, business logic, and
    persistence.
-   Use DTOs where they provide a clean API boundary rather than
    exposing persistence entities directly.
-   Enforce short-code uniqueness at the database level.
-   Design generated-code collision handling to remain correct under
    concurrent requests.
-   Keep expiration and link-state rules in appropriate business logic
    rather than duplicating them across controllers.
-   Centralise API exception/error mapping where practical.
-   Store timestamps using a documented and consistent time strategy.
-   Keep environment-specific configuration outside source code.
-   Do not commit credentials, secrets, generated build output, IDE
    state, or machine-specific configuration.
-   Provide automated unit, API, and PostgreSQL-backed integration
    tests.
-   Configure GitHub Actions to execute appropriate automated quality
    checks.
-   Dependencies must be justified and proportionate.
-   Follow the Portfolio Repository Standard, AI Development Guide, and
    Definition of Done.

## Architecture

ShortStack should use a conventional layered backend architecture:

``` text
HTTP Client
    |
    v
Controller / API Layer
    |
    v
Service / Domain Logic
    |
    v
Repository / Persistence Layer
    |
    v
PostgreSQL
```

Expected responsibilities:

-   **Controller/API layer:** HTTP request/response handling, API DTOs,
    status codes, redirects, and delegation to services.
-   **Service layer:** creation, resolution, expiration,
    deletion/deactivation, collision policy, and business decisions.
-   **Repository layer:** persistence access without owning business
    rules.
-   **Persistence model:** database representation and database-enforced
    constraints.
-   **DTOs:** request and response contracts separated from persistence
    implementation.
-   **Validation:** request validation at appropriate boundaries and
    business validation where required.
-   **Exception/error handling:** consistent translation of application
    failures into HTTP responses.
-   **Migration layer:** explicit creation and evolution of the
    PostgreSQL schema.

A likely source organisation may include:

``` text
controller/
service/
repository/
model/
dto/
exception/
```

Exact packages and class names should be refined during scaffolding
without creating unnecessary abstraction.

## API Design Direction

The initial design should consider operations equivalent to:

``` text
POST   /api/links
GET    /api/links/{code}
DELETE /api/links/{code}

GET    /{code}
```

This is a design direction, not a frozen contract. The project should
deliberately decide and document:

-   final resource paths;
-   creation response status and representation;
-   redirect status semantics;
-   missing-link behaviour;
-   expired-link behaviour, including whether `410 Gone` is appropriate;
-   deletion versus deactivation semantics;
-   custom-alias validation rules;
-   generated-code format and length;
-   collision retry/failure policy.

These decisions should be explainable during portfolio review.

## Data Model Direction

A shortened link will require data conceptually similar to:

``` text
ShortLink
-------------------------
id
code
destination_url
created_at
expires_at
```

Additional state such as an `active` field should only be introduced if
required by the chosen deletion/deactivation semantics.

The database must enforce uniqueness of `code`. Identifier strategy,
column types, constraints, indexes, timestamp representation, and
deletion model should be decided during schema design and captured
through migrations.

## Repository Structure

A likely repository structure is:

``` text
shortstack/
├── .github/
│   └── workflows/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
│       └── java/
├── .gitignore
├── compose.yaml
├── LICENSE
├── PROJECT.md
├── README.md
└── pom.xml
```

Migration files should live under the appropriate application resources
directory for the selected migration tool. Additional directories should
only be introduced where they provide clear value.

## Testing Strategy

Testing is a major engineering objective and should be implemented
alongside functionality.

### Unit Testing

Unit tests should cover service-level behaviour such as:

-   short-code generation;
-   generated collision handling;
-   custom-alias behaviour;
-   expiration logic;
-   link-state rules;
-   important business rules;
-   error conditions.

### Web / API Testing

HTTP-facing tests should verify:

-   request validation;
-   request and response representations;
-   expected status codes;
-   redirect behaviour;
-   structured API errors;
-   custom-alias conflicts;
-   missing resources;
-   expired resources;
-   deletion/deactivation semantics.

### Integration Testing

Integration tests should use PostgreSQL and verify important persistence
concerns including:

-   migrations;
-   repository behaviour;
-   uniqueness constraints;
-   persistence across relevant operations;
-   collision-related database behaviour;
-   timestamp and expiration persistence.

A container-based integration-test approach may be used where it
provides reliable and maintainable PostgreSQL-backed testing.

### Regression Testing

Defects discovered during development should receive regression tests
where practical.

### Continuous Integration

GitHub Actions should run applicable build, test, and quality checks.

``` text
GitHub Actions
      |
      v
Build / Static Checks
      |
      v
Unit & API Tests
      |
      v
PostgreSQL-backed Integration Tests
```

Meaningful behavioural and integration testing takes priority over
arbitrary coverage targets.

## Development Constraints

-   Follow Portfolio Definition of Done.
-   Follow Repository Standard.
-   Follow AI Development Guide.
-   Do not introduce technologies without justification.
-   Do not expand scope without approval.
-   Do not add a frontend during Version 1.
-   Do not convert the project into a microservice or distributed-system
    exercise.
-   GitHub issues are the unit of implementation work.
-   AI-completed implementation moves to **Review**, not directly to
    **Done**.
-   Codex work must remain bounded by the current issue and this project
    brief.
-   Significant API, architecture, schema, concurrency, and scope
    decisions require human involvement or review.
-   Sam should be able to explain the finished API contract,
    architecture, persistence model, collision strategy, expiration
    semantics, database constraints, testing strategy, and significant
    implementation decisions.

### AI Ownership Principle

ShortStack should use AI as an implementation accelerator rather than
delegating ownership of backend design.

API semantics, schema/domain design, collision strategy, expiration
semantics, architecture, concurrency/correctness decisions, and
significant reviews should normally use `owner:sam` or `owner:pair`.

Well-specified scaffolding, repetitive implementation, test expansion,
configuration, Docker/CI implementation, and documentation may often use
`owner:ai`.

Ownership should still be assigned per issue according to the actual
work.

## Milestones

### M1 --- Foundation & Persistence

Expected outcomes:

-   Spring Boot project and build configuration;
-   repository structure;
-   application configuration strategy;
-   PostgreSQL local-development environment;
-   database migration tooling;
-   initial schema migration;
-   persistence model;
-   repository layer;
-   initial test infrastructure;
-   baseline CI/build configuration.

The initial API/domain and schema decisions should receive explicit
human review before later work depends on them.

### M2 --- Core Shortening Service

Expected outcomes:

-   link-creation API;
-   generated short-code strategy;
-   persistence of created links;
-   unique-code enforcement;
-   generated collision handling;
-   metadata retrieval;
-   short-code resolution;
-   HTTP redirect behaviour;
-   core unit and API tests.

### M3 --- Backend Robustness

Expected outcomes:

-   custom aliases;
-   custom-alias conflicts;
-   expiration support;
-   expired-link semantics;
-   deletion or deactivation behaviour;
-   URL and request validation;
-   centralised exception/error handling;
-   PostgreSQL-backed integration testing;
-   concurrency/correctness review;
-   edge-case and regression testing;
-   appropriate quality/static checks.

### M4 --- Portfolio Polish

Expected outcomes:

-   Docker/local-development workflow verified;
-   CI fully passing;
-   clean-environment run instructions verified;
-   README completed;
-   API examples documented;
-   important HTTP semantics documented;
-   architecture and persistence decisions documented;
-   testing strategy documented;
-   repository metadata/topics reviewed;
-   final Definition of Done review;
-   final human code review;
-   project retrospective;
-   portfolio-roadmap update.

## Completion Criteria

ShortStack is portfolio-ready when:

-   all approved Version 1 functional requirements are implemented;
-   the API contract and important HTTP semantics are documented;
-   generated and custom short-code collisions follow the documented
    policy;
-   short-code uniqueness is enforced by PostgreSQL;
-   expiration behaviour is implemented and documented;
-   deletion/deactivation semantics are implemented and documented;
-   invalid input and common failure conditions return controlled,
    appropriate responses;
-   schema changes are represented through explicit migrations;
-   automated unit, API, and PostgreSQL-backed integration tests cover
    important behaviours;
-   CI passes;
-   the application and PostgreSQL environment start using the
    documented workflow;
-   representative API operations and redirects have been manually
    verified;
-   the repository follows the Portfolio Repository Standard;
-   the README explains the problem, architecture, setup, API usage,
    testing, and important design decisions;
-   the project contains no secrets, temporary development artefacts,
    generated build output, or unnecessary machine-specific files;
-   significant AI-generated code has been reviewed and is understood by
    Sam;
-   Sam can explain the API, architecture, schema, collision strategy,
    expiration rules, constraints, and testing approach;
-   all required Version 1 issues satisfy the Portfolio Definition of
    Done;
-   the repository has completed a final portfolio review;
-   `PORTFOLIO_ROADMAP.md` has been updated with the project's outcome
    and any resulting changes to future plans.
