---
name: tdd
model: claude-sonnet-4-6
allowed-tools: Read, Write, Edit, Bash
description: >-
  Run ONE TDD inner-loop cycle — RED → GREEN → REFACTOR → CHALLENGE → STOP
  (Step 3 of the development process). Use to drive a single failing test to
  green with the minimum production code, then propose the next edge case. One
  cycle per invocation; stops and waits for the user before continuing.
argument-hint: "<test class or method to drive>"
---

Run ONE TDD cycle for: $ARGUMENTS

Read CLAUDE.md for architecture and testing conventions before writing any code.

## RED — confirm the failure

Run the failing test first. Read the failure message.
Understand WHY it fails before writing any production code.
If the test already passes, STOP — something is wrong.

## GREEN — minimum code to pass

Write the MINIMUM production code to make this one test pass.
Minimum means minimum:
- No extra methods "while we're here"
- No anticipating the next test
- No abstractions until refactoring demands them
- Hard-code if that's all this test requires

Respect architecture boundaries:
- Domain code: pure Java, no Spring, no JPA
- Controllers: thin delegation, no business logic
- Persistence: JPA entities stay in adapter layer
- Let the scoped rules guide you

## REFACTOR — clean up with confidence

All tests are green. Now improve the code:
- Remove duplication
- Extract clear names
- Simplify conditionals
- Check that the code reads like the spec

Run ALL tests after refactoring — not just the current one.
If anything breaks, fix it before moving on.

## CHALLENGE — drive out edge cases

Before stopping, ask yourself:
"What else should this do?"
"What input could break this?"

Consider: zero/empty input, not-found, boundary values, rounding, invalid state,
null, negative amounts, duplicate requests.

Propose at least one edge case to the user.
If approved, that edge case becomes the next RED.

## STOP

Report what you changed:
- Which test is now passing
- What production code you wrote or modified
- What you refactored
- What edge case you propose next

Do NOT write additional tests beyond the one specified.
Do NOT add unrequested features or "improvements".
Do NOT modify any existing test to make it pass — fix the production code instead.

Wait for the user before starting the next cycle.

---

# Test patterns by architecture layer (Spring Boot 4 / Spring Framework 7)

Reference for writing the RED test at the right level. This service runs on **Spring Boot 4.1, Spring Framework 7, JUnit Jupiter 6** (see `build.gradle`). Boot 4 changed two things that older guides and training data get wrong:

1. **`@MockBean` / `@SpyBean` are removed.** Use `@MockitoBean` / `@MockitoSpyBean` from `org.springframework.test.context.bean.override.mockito`.
2. **Test slices are modularized** into separate `spring-boot-<tech>-test` jars and their packages moved — `@WebMvcTest` is now `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` (was `...test.autoconfigure.web.servlet`). `spring-boot-starter-test` no longer pulls the web or JPA slices; add the matching test starter.

Prefer the AssertJ-based **`MockMvcTester`** (`org.springframework.test.web.servlet.assertj`) over the old `mockMvc.perform(...).andExpect(...)` chain.

> The examples use a small, self-contained **order-line pricing** resource as a stand-in for whatever feature you're testing: `POST /api/order-lines` takes `{ sku, quantity, unitPrice }` and returns `{ total, breakdown: { unitPrice, quantity, bulk } }`. Two deliberately simple, checkable rules run through every layer: **the line total is `unitPrice × quantity`**, and **an order of 10 or more items is a "bulk" order**. Swap in your own types and rules — the annotations, layering and imports are what carry over. Every snippet was compiled and run against the versions above.

## Pick the tier deliberately

Push each rule to the **lowest tier that can prove it**. Most rules are plain logic and belong in a service test with no Spring context (milliseconds). Reach for a slice or full-context test only to prove wiring you can't prove otherwise (HTTP mapping, serialization, security).

| Layer under test | Annotation | Spring context | Proves |
| --- | --- | --- | --- |
| Value object / enum | none — plain JUnit | no | Pure data and small derivations (classification, rounding, formatting) |
| Service / business logic | none — plain JUnit (+ Mockito for collaborators) | no | The calculation / orchestration rules |
| Controller / web layer | `@WebMvcTest(XController.class)` | web slice only | Routing, (de)serialization, status codes, validation — collaborators mocked |
| JSON DTO contract | `@JsonTest` | json slice only | Field names and shape of request / response bodies |
| Repository (only with a DB) | `@DataJpaTest` | jpa slice + embedded DB | Queries and mappings against a real (in-memory) database |
| Acceptance / full HTTP cycle | `@SpringBootTest` + `@AutoConfigureMockMvc` | full app, mock servlet | The whole wired path end to end, no network |
| Real network round-trip | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` | full app, real server | The app over actual HTTP (smoke / contract) |

Don't assert the same rule in two tiers: acceptance tests verify the HTTP contract, service tests verify the logic.

## Dependencies (build.gradle)

The slice and acceptance examples need the web test starter. `spring-boot-starter-test` alone covers plain value-object and service unit tests.

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
// Boot 4: provides @WebMvcTest, @AutoConfigureMockMvc, MockMvcTester, @AutoConfigureRestTestClient.
// Pulls spring-boot-starter-test transitively.
testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
testImplementation 'org.assertj:assertj-core'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
// Only with a persistence layer:
// testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
```

Managed versions resolved by Boot 4.1.0: Spring Framework 7.0.2, JUnit Jupiter 6.1.0, AssertJ 3.27.7, Mockito 5.23.0.

## 1. Value object / enum — plain JUnit, no Spring

Pure Java; test with JUnit + AssertJ and nothing else. Test the rule at its boundary — the value where the classification flips.

```java
package com.example.app.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderSizeTest {

    @Test
    @DisplayName("The one where an order of 10 or more items is a bulk order")
    void tenOrMoreItemsIsBulk() {
        assertThat(OrderSize.forQuantity(9)).isEqualTo(OrderSize.STANDARD);
        assertThat(OrderSize.forQuantity(10)).isEqualTo(OrderSize.BULK);
    }
}
```

## 2. Service — plain JUnit (the workhorse tier)

Construct the service directly — no `@SpringBootTest`, no application context. Most rules live and are driven here. Assert money with `isEqualByComparingTo` (scale-insensitive `compareTo`), never `isEqualTo`.

```java
package com.example.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.app.model.LineTotal;
import com.example.app.model.OrderLine;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderLineServiceTest {

    private final OrderLineService service = new OrderLineService();

    @Test
    @DisplayName("The one where 3 items at £2.50 total £7.50")
    void totalIsUnitPriceTimesQuantity() {
        LineTotal result = service.price(new OrderLine("A1", 3, new BigDecimal("2.50")));

        assertThat(result.total()).isEqualByComparingTo("7.50");
        assertThat(result.breakdown().bulk()).isFalse();
    }
}
```

**When the service has a collaborator,** mock it with plain Mockito (`mockito-junit-jupiter` is on the classpath via `spring-boot-starter-test`). Still no Spring context — `@ExtendWith(MockitoExtension.class)` is enough:

```java
package com.example.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogPricerTest {

    @Mock
    private PriceCatalog catalog;   // a collaborator interface

    @Test
    @DisplayName("The one where the total is the catalog's unit price times the quantity")
    void multipliesCatalogPriceByQuantity() {
        given(catalog.unitPriceFor("A1")).willReturn(new BigDecimal("2.50"));

        assertThat(new CatalogPricer(catalog).totalFor("A1", 4)).isEqualByComparingTo("10.00");
    }
}
```

## 3. Controller — `@WebMvcTest` slice

Loads only the web layer for one controller. The service is replaced with a Mockito mock via **`@MockitoBean`**, so this proves routing, JSON binding and status codes — not business logic (note the stubbed total is asserted, not a real calculation). `MockMvcTester` is auto-configured because AssertJ is on the classpath.

```java
package com.example.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.app.model.LineTotal;
import com.example.app.model.OrderLine;
import com.example.app.service.OrderLineService;
import java.math.BigDecimal;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(OrderLineController.class)
class OrderLineControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OrderLineService service;

    @Test
    @DisplayName("The one where the endpoint returns the line total as JSON")
    void returnsLineTotalAsJson() {
        given(service.price(any(OrderLine.class))).willReturn(
                new LineTotal(new BigDecimal("7.50"),
                        new LineTotal.Breakdown(new BigDecimal("2.50"), 3, false)));

        assertThat(mvc.post().uri("/api/order-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "sku": "A1", "quantity": 3, "unitPrice": 2.50 }
                        """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.total")
                .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("7.50");
    }
}
```

For money fields, `extractingPath(...).convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo(...)` keeps the `compareTo` rule. Don't use `.asNumber().isEqualTo(new BigDecimal(...))` — that compares a parsed `Double` to a `BigDecimal` by `equals()` and fails.

## 4. JSON contract — `@JsonTest` slice

Pins the serialized shape of a DTO (field names, nesting) without a controller. `JacksonTester` is injected by the slice.

```java
package com.example.app.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.app.model.LineTotal;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class LineTotalJsonTest {

    @Autowired
    private JacksonTester<LineTotal> json;

    @Test
    @DisplayName("The one where the total and its nested breakdown are serialised")
    void serialisesTotalWithBreakdown() throws Exception {
        LineTotal lineTotal = new LineTotal(new BigDecimal("7.50"),
                new LineTotal.Breakdown(new BigDecimal("2.50"), 3, false));

        assertThat(json.write(lineTotal))
                .hasJsonPathNumberValue("@.total")
                .extractingJsonPathBooleanValue("@.breakdown.bulk").isFalse();
    }
}
```

## 5. Repository layer — `@DataJpaTest` (only if a database is added)

This service has no persistence layer today, so there's nothing to test here yet. When a database is introduced, add `spring-boot-starter-data-jpa` (main) and `spring-boot-starter-data-jpa-test` (test). `@DataJpaTest` (`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`) starts an embedded DB, is transactional, and rolls back each test. Inject the repository plus a `TestEntityManager` to set up rows; keep JPA entities in the repository layer per the architecture rules.

## 6. Acceptance test — `@SpringBootTest` + `MockMvcTester`, structured with `@Nested`

**One test class per story/feature** in `acceptance/`, named `<Feature>AcceptanceIT`. Loads the **whole** application with the real service wired in and drives it through the full servlet stack with no network (`webEnvironment = MOCK`, the default). Assert the HTTP contract here, not the logic the service test already covers.

Mirror the spec so the report reads like Example Mapping:

- The **outer class** = the feature, with a feature-level `@DisplayName`.
- **One JUnit 6 `@Nested` class per rule**, named with a `@DisplayName` stating the rule.
- **One `@Test` per example / counter-example** under its rule, each a `"The one where…"` `@DisplayName`.

`@Nested` classes are non-static inner classes; Spring's `@NestedTestConfiguration` defaults to `INHERIT`, so the outer class's context and `@Autowired` / `@MockitoBean` fields (here `mvc`) are shared with every nested class. Use a `MvcTestResult` when one response needs several assertions (it exchanges once); a shared private helper keeps request-building DRY.

```java
package com.example.app.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Pricing an order line")
class OrderLineAcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult price(String body) {
        return mvc.post().uri("/api/order-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    @Nested
    @DisplayName("The line total is the unit price times the quantity")
    class UnitPriceTimesQuantity {

        @Test
        @DisplayName("The one where 3 items at £2.50 total £7.50")
        void threeItemsAtTwoFifty() {
            assertThat(price("""
                    { "sku": "A1", "quantity": 3, "unitPrice": 2.50 }
                    """))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.total")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("7.50");
        }
    }

    @Nested
    @DisplayName("An order of 10 or more items is a bulk order")
    class BulkOrders {

        @Test
        @DisplayName("The one where 10 items is a bulk order")
        void tenItemsIsBulk() {
            assertThat(price("""
                    { "sku": "A1", "quantity": 10, "unitPrice": 2.50 }
                    """))
                    .bodyJson().extractingPath("$.breakdown.bulk").asBoolean().isTrue();
        }

        @Test
        @DisplayName("The one where 9 items is not a bulk order (counter-example)")
        void nineItemsIsNotBulk() {
            assertThat(price("""
                    { "sku": "A1", "quantity": 9, "unitPrice": 2.50 }
                    """))
                    .bodyJson().extractingPath("$.breakdown.bulk").asBoolean().isFalse();
        }
    }
}
```

In the report this nests as **Pricing an order line › An order of 10 or more items is a bulk order › The one where 10 items is a bulk order** — each rule a collapsible group, each example a line under it.

## 7. Real HTTP round-trip — `RestTestClient` (smoke / contract)

To prove the app over a real socket, use `RANDOM_PORT` with **`RestTestClient`** — the Spring Framework 7 successor to `TestRestTemplate`. Use sparingly; it's the slowest tier. `@AutoConfigureRestTestClient` comes from `spring-boot-starter-webmvc-test`.

```java
package com.example.app.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class OrderLineRestClientIT {

    @Autowired
    private RestTestClient restClient;

    @Test
    @DisplayName("The one where a real HTTP round-trip returns 200 and the line total")
    void realHttpRoundTrip() {
        restClient.post().uri("/api/order-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        { "sku": "A1", "quantity": 3, "unitPrice": 2.50 }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(7.50);
    }
}
```

## Naming & convention reminders

- Method names state the rule (`tenItemsIsBulk`), never a number (`testCalculate3`).
- `@DisplayName` is a plain-language, Example-Mapping line: `"The one where 3 items at £2.50 total £7.50"`.
- Acceptance classes end in `IT`; one class per story/feature in `acceptance/`, a `@Nested` class per rule (named by `@DisplayName`), one `@Test` per example / counter-example.
- Money: `BigDecimal`, scale 2, `RoundingMode.HALF_UP`, assert with `isEqualByComparingTo`.
