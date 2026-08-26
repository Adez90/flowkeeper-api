# flowkeeper-api

The FlowKeeper backend — a single Spring Boot service, organised into domain modules
internally rather than split into microservices. Owns the domain model, the REST +
OpenAPI surface, and all access to Postgres. Validates Keycloak-issued tokens as an
OAuth2 resource server; it never sees a password.

Full architecture, the reasoning behind every stack choice, and the security baseline
this repo follows are in the [FlowKeeper Blueprint](https://claude.ai/code/artifact/4121aef7-c600-4f2d-bf55-fb27e6fba16f).

## Stack

Java 25 · Spring Boot 4 · Spring MVC (virtual threads, not WebFlux) · Spring Data JPA ·
Flyway · PostgreSQL · Spring Security (OAuth2 resource server)

## Running locally

1. Start Postgres + Keycloak from `flowkeeper-infra` (`docker compose up -d` there).
2. `./mvnw spring-boot:run`

The default `application.yml` points at `localhost:5432` and the local Keycloak realm
out of the box — no extra config needed for a first run.

Confirm it's alive: `GET http://localhost:8080/actuator/health` (public). Confirm auth
is wired end-to-end: `GET http://localhost:8080/api/v1/ping` with a Keycloak-issued
bearer token — returns the token's subject.

## Schema

Owned entirely by Flyway (`src/main/resources/db/migration`) — Hibernate is set to
`validate`, never `update`. `V1__init_schema.sql` creates the core tables: accounts
(Personal or Organisation), the Department/Group hierarchy, account membership +
role, the configurable event-type taxonomy (seeded with a baseline set), and events.

## What's here vs. what's next

- [x] Project skeleton, OAuth2 resource server security config (deny-by-default)
- [x] Core schema migration (accounts, departments, groups, users, event types, events)
- [ ] Domain entities/repositories/services for the schema above
- [ ] Real REST endpoints + generated OpenAPI spec
- [ ] Testcontainers-backed integration tests (the placeholder test in
      `src/test` is intentionally not a real one yet — see its comment)

## Docker

`Dockerfile` builds a runnable image (`docker build -t flowkeeper-api .`); used by
`flowkeeper-infra`'s production Compose file via `${API_IMAGE}`.
