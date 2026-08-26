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

## Conventions

- **Logging**: SLF4J via each class's own `Logger`. `se.flowkeeper` defaults to
  INFO in `application.yml` — business-relevant events (a user registered, an
  event completed) log at INFO, detail at DEBUG, genuine inconsistencies at
  ERROR. Raise `se.flowkeeper` to DEBUG locally rather than lowering `root`.
- **Testing**: every vertical slice gets a fast unit test against the service
  logic (Mockito, no Spring context) and an end-to-end test through real HTTP
  + a real, Flyway-migrated Postgres (Testcontainers, via `AbstractIntegrationTest`).
  See `registration/` for the pattern to follow.

## What's here vs. what's next

- [x] Project skeleton, OAuth2 resource server security config (deny-by-default)
- [x] Core schema migration (accounts, departments, groups, users, event types, events)
- [x] Registration: first-login provisioning of a User + Personal account + OWNER
      membership (`POST /api/v1/registration`), unit + integration tested
- [x] `/me`: resolves the signed-in user's profile and every account they belong
      to, for use on every app open after login (`GET /api/v1/me`)
- [x] Events: log one (`POST /api/v1/events`), close it out
      (`POST /api/v1/events/{id}/complete`), list for the landing page
      (`GET /api/v1/events?accountId=&status=`), and the type picker
      (`GET /api/v1/event-types?accountId=`) — everything the "landing page"
      (ongoing events + create) needs
- [ ] Day/week/month statistics rollups (the separate stats view a client
      navigates to from the landing page)
- [ ] Organisation/Department/Group management endpoints
- [ ] Generated OpenAPI spec for the web/mobile clients

## Docker

`Dockerfile` builds a runnable image (`docker build -t flowkeeper-api .`); used by
`flowkeeper-infra`'s production Compose file via `${API_IMAGE}`.
