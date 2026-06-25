---
name: accept
model: claude-sonnet-4-6
allowed-tools: Read, Write, Edit, Bash
description: >-
  Write a failing acceptance test for the NEXT spec rule (Step 2 of the
  development process). Use after a spec in doc/specs/ is finalised and before
  any production code is written for that rule. One rule at a time — the test
  must fail for the right reason.
argument-hint: "<rule name> @doc/specs/<feature>.md"
---

Write a failing acceptance test for: $ARGUMENTS

Read CLAUDE.md for project conventions before writing anything.
Re-read the spec file to understand the full rule, its examples, and its counter-examples.

## Structure

One test class per story/feature, named `<Feature>AcceptanceIT`.
One `@Nested` inner class per rule — name it after the rule.
One `@Test` per example AND per counter-example from the spec.

Use `@DisplayName` with the spec's exact business language:
- Outer class: the feature name
- `@Nested` class: the rule
- Method: the "The one where…" text from the spec (counter-examples too)

`@Nested` classes inherit the outer class's Spring context (`@NestedTestConfiguration`
defaults to `INHERIT`), so annotate and `@Autowired` the test client once on the outer class.
See `.claude/skills/tdd/SKILL.md` → "Acceptance test" for the full worked template.

## How to test

Test through the REST API. This service runs on **Spring Boot 4** (JUnit 6), so use
`@SpringBootTest` + `@AutoConfigureMockMvc` and inject the AssertJ-based `MockMvcTester`
(`org.springframework.test.web.servlet.assertj.MockMvcTester`) — NOT the old
`mockMvc.perform(...).andExpect(...)` chain, and NOT `@MockBean` (removed in Boot 4).

`@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`
and needs the `spring-boot-starter-webmvc-test` test dependency.

Send real HTTP requests. Assert real HTTP responses.
NEVER call services or domain objects directly — this is an acceptance test, not a unit test.

Skeleton (replace the placeholder `<Feature>` / `<rule>` / endpoint with the spec's):

```java
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("<feature name>")
class <Feature>AcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    @Nested
    @DisplayName("<the rule, in the spec's words>")
    class <Rule> {
        @Test
        @DisplayName("The one where <the example>")
        void <example>() {
            MvcTestResult result = mvc.post().uri("/api/<resource>")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { ...request fields from the example... }
                            """)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$.<field>")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("<expected>");
        }
    }
}
```

Assert exact values from the spec examples. For money, compare with `compareTo`, never
`equals`: `extractingPath("$.amount").convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo("1.60")`.
Do NOT use `.asNumber().isEqualTo(new BigDecimal(...))` — it compares a parsed `Double`
to a `BigDecimal` and fails.

## What NOT to do

Do NOT write production code. The test MUST FAIL.
A passing test means you tested nothing.
Do NOT write tests for all rules at once.
One rule only — the one specified in the arguments.
Do NOT invent examples beyond what the spec provides.
The spec is the contract.
Do NOT use mocks in acceptance tests.
Wire the full stack: controller → service → domain → persistence.

## When you're done

Run the test. Confirm it fails for the RIGHT reason:
- Missing endpoint → 404 or compilation error (good)
- Wrong value → not yet, the endpoint shouldn't exist
- Test passes → something is wrong, investigate

Report: which rule you tested, how many examples, and the failure reason.

STOP. Do not proceed to implementation.
