# Spring Boot SDD Starter

A starter for building Spring Boot services with a Spec-Driven Development (SDD) workflow. It ships with a `.claude/` toolchain (hooks, agents, commands) that drives a test-first workflow, enforces specs, and guards architecture boundaries.

<!-- ADAPT: This file is the single most important input for Claude — it's read
     before any work. Every section below is a working default for a standard
     Spring Boot REST service. Edit the content to match your project; the
     HTML comments tell you what to change in each section. A worked example
     (a shipping cost calculator) sits at the bottom — delete it once your own
     sections are accurate. -->

## Project Overview

A Spring Boot REST service built and tested with Gradle.

- **Build tool:** Gradle (`./gradlew`)
- **Framework:** Spring Boot 4.1.0 (Spring Framework 7), Java 21 — see `build.gradle`
- **Testing:** JUnit 6.1.0 (Jupiter) + AssertJ

<!-- ADAPT: Replace this paragraph with one or two sentences describing what
     your service actually does. Keep the build/framework facts in sync with
     build.gradle. -->

## Architecture

Standard Spring Boot layered architecture. Keep it simple — controller calls service, service uses models:

- **controller/** — REST endpoints. Receives requests, delegates to a service, returns responses. No business logic.
- **service/** — All business logic and orchestration. Testable without a Spring context. Keeps HTTP and persistence concerns out.
- **model/** — Domain objects, request/response DTOs, enums, value objects. Immutable where practical; no business logic.
- **repository/** — Data access. Only present when the project uses a database.

Layer rules: controllers never contain business logic; services never import controller-layer or web types; models hold data, not logic. The `architecture-guardian` agent enforces these boundaries.

<!-- ADAPT: If you use a different style (hexagonal: ports/adapters/domain,
     clean architecture: entities/usecases/interfaces, modular monolith,
     microservices), replace the layers and rules above to describe it, and
     update .claude/agents/architecture-guardian.md to match. -->

## Processing Order

When a feature applies a fixed sequence of steps, the code and the tests must follow that exact order, and the order is documented here so it can't drift.

<!-- ADAPT: List your domain's processing/calculation chains here, in order —
     e.g. "Price: base price → discount → tax → total" or
     "Request: validate → enrich → persist → notify". Delete this section if
     no feature in your domain has an order-dependent chain. -->

## Monetary / Numeric Precision

For money and other exact decimal values:

- Use `BigDecimal` — never `double` or `float`.
- Scale: 2 decimal places. Rounding: `RoundingMode.HALF_UP`.
- Compare with `compareTo()`, not `equals()` (`BigDecimal` is scale-sensitive).
- Assert exact values in tests — no floating-point tolerance.

<!-- ADAPT: Adjust the scale and rounding mode to your domain's rules. Delete
     this whole section if the project handles no exact decimal values. -->

## API Design

REST conventions for this service:

- Endpoints accept and return JSON (`Content-Type: application/json`).
- Responses return a breakdown of intermediate steps where relevant, so every stage is visible and testable rather than just a final value.
- Status codes are explicit: `200` success, `400` validation error, `401`/`403` auth, `404` not found.

```
POST /api/<resource>
Content-Type: application/json

{ ...request fields... }

Response 200:
{ ...response fields, including a breakdown of intermediate steps... }
```

<!-- ADAPT: Replace the endpoint, the request/response shapes, and the status
     codes with your real API. Include at least one concrete example request
     and response — the worked example at the bottom of this file shows the
     level of detail that helps. -->

## Testing Conventions

Two tiers of tests:

- **Acceptance tests** (`src/test/java/.../acceptance/`): `@SpringBootTest` + `MockMvc`. One test class per feature. Test the full HTTP request/response cycle.
- **Service / unit tests** (`src/test/java/.../service/`): Plain JUnit 6, no Spring context. Test business logic directly.

Conventions:

- Test method names describe the business rule (`domesticOrderOver75GetsFreeShipping`), not a number (`testCalculate3`).
- Use `@DisplayName` with plain-language, Example-Mapping-style descriptions: `"The one where a 3kg European parcel costs £7.49"`.
- Acceptance tests verify the HTTP contract; service tests verify business logic. Don't duplicate the same assertions across both tiers.
- Run the suite with `./gradlew test`. The `/accept` and `/tdd` workflows run it for you as part of each cycle, so you see each test go red and green yourself.

<!-- ADAPT: Change the test directories, the build command, and the example
     names if your conventions differ. Keep the two-tier structure — the
     agents and commands assume it. -->

## Spec Files

Business rules live in `docs/specs/` as markdown, one file per feature (`<feature>.specs.md`). Every rule in a spec has at least one acceptance test. Use `/discover` to turn a feature idea into a spec, then `/accept` and `/tdd` to implement it.

<!-- ADAPT: Change the spec directory if it isn't docs/specs/. The
     one-rule-one-test invariant is enforced by the spec-compliance agent —
     keep it. -->

## API Documentation (Swagger/OpenAPI)

springdoc-openapi is included in `build.gradle`. When the app runs, Swagger UI is served at `/swagger-ui.html`, generated automatically from the controllers. Add `@Operation` / `@ApiResponse` annotations for richer descriptions.

<!-- ADAPT: Remove this section and the springdoc dependency in build.gradle if
     the project doesn't expose a REST API. -->

## Security

Spring Security is commented out in `build.gradle` until needed. Adding it locks down all endpoints immediately — every existing test returns 401 until a `SecurityFilterChain` is configured. When you add auth, exclude Swagger UI paths if you want docs to stay public, and update existing acceptance tests to send credentials (or add a test security config that permits requests for business-logic tests).

<!-- ADAPT: Document your chosen mechanism (API key header, JWT, OAuth2),
     the roles, and the rule for each (no creds → 401, wrong role → 403, etc.)
     once you enable it. Delete this section if the service is unauthenticated. -->

## The `.claude/` Toolchain

A test-first workflow: `/discover` (idea → spec) → `/accept` (failing acceptance
test) → `/tdd` (drive it green), reviewed by the `spec-compliance` and
`architecture-guardian` agents and guarded by a PreToolUse hook
(`.claude/hooks/protect-files.sh`). Tests run inside the `/accept` and `/tdd`
workflows, not in a hook, so each red/green step stays visible.

<!-- ADAPT: Swap the Gradle test command in /accept and /tdd if you don't use
     Gradle, and add your sensitive paths to protect-files.sh. Keep the .claude/
     structure, the hook exit-2-to-block convention, the $ARGUMENTS placeholder
     in commands, and docs/specs/. -->

---