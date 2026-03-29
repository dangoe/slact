# Skill: Java Testing

You are an expert in Java testing. Apply all guidelines in this skill when writing, reviewing, or reasoning about Java test code.

> **Extension:** Before proceeding, check whether `.github/skills/java-testing.extension.md` exists in the repository root. If it does, read it and incorporate its project-specific context alongside the guidelines below.

---

## Test Types & Source Sets

| Type | Location | Naming | Run command |
|------|----------|--------|-------------|
| Unit test | `src/test/java/` | `*Test` | `./gradlew test` / `mvn test` |
| Integration test | `src/integrationTest/java/` | `*IT` | `./gradlew integrationTest` / `mvn verify` |

- **Unit tests** cover a single class in isolation; mock all collaborators.
- **Integration tests** exercise multiple layers together, including real infrastructure (databases, queues) via Testcontainers.
- Never mix unit and integration concerns in the same test class.

## Test Framework Stack

- **JUnit 5** (Jupiter): `@Test`, `@Nested`, `@DisplayName`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll`
- **AssertJ**: fluent assertions (`assertThat(...)`) — prefer over raw JUnit assertions for readability
- **Mockito**: `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, `when(...).thenReturn(...)`, `verify(...)`
- **Awaitility**: async assertions (`await().atMost(...).until(...)`)
- **Testcontainers**: real infrastructure in integration tests

## Structure: `@Nested` + `@DisplayName`

Every test class **must** use `@Nested` classes to group tests by method under test and by scenario. Every class and every test method **must** carry a `@DisplayName`.

```
@DisplayName("ClassName")
class ClassNameTest {

    @Nested
    @DisplayName("methodName()")
    class MethodName {

        @Nested
        @DisplayName("given <precondition>")
        class GivenSomePrecondition {

            @Test
            @DisplayName("when <action>, then <outcome>")
            void whenAction_thenOutcome() { ... }
        }
    }
}
```

The combined labels must read as a coherent English sentence in test reports:
> `ClassName > methodName() > given a valid input > when called, then returns expected result`

## Given / When / Then

Every test body **must** contain explicit `// Given`, `// When`, `// Then` inline comments:

```java
@Test
@DisplayName("when the user exists, then returns the expected user")
void whenUserExists_thenReturnsUser() {
    
    var userId = "user-123";
    when(mockRepository.findById(userId)).thenReturn(Optional.of(new User(userId)));

    
    var result = service.getUser(userId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(userId);
}
```

## Naming Conventions

- Test class: `PascalCase` + `Test` suffix (`OrderServiceTest`)
- `@Nested` class: `PascalCase`, named after method or scenario (`GetOrderById`, `GivenExpiredOrder`)
- Test method: GWT camelCase — `whenAction_thenOutcome()` or `givenContext_whenAction_thenOutcome()`
- Constants: `UPPER_SNAKE_CASE` (`DEFAULT_TIMEOUT_MS`)
- Mocks: `mock` prefix or `Mock` suffix (`mockRepository`, `orderRepositoryMock`)

## Edge Case Coverage

Every non-trivial method should have tests for:
- Happy path (valid inputs, expected outcome)
- Null or blank inputs
- Empty collections / zero values
- Boundary values (min, max, off-by-one)
- Exception / error paths
- Concurrent / async completion (where applicable)

## Mockito Guidelines

- Use `@ExtendWith(MockitoExtension.class)` on unit test classes.
- Stub only what the test exercises; avoid over-specifying interactions.
- Use `verify(mock, times(n))` to assert side effects, not to duplicate assertion logic.
- Prefer `@Captor` + `ArgumentCaptor` over inline `any()` matchers when the argument value matters.
- Do **not** mock value objects, DTOs, or records — instantiate them directly.

## AssertJ Guidelines

- Use `assertThat(actual).isEqualTo(expected)` — never `assertEquals(expected, actual)`.
- Use `.isInstanceOf(ExceptionClass.class)` and `.hasMessageContaining(...)` for exception assertions.
- Chain assertions fluently rather than multiple separate `assertThat` calls.
- For collections: use `.containsExactly(...)`, `.containsExactlyInAnyOrder(...)`, `.hasSize(n)`.

## Async Testing

- Use **Awaitility** for assertions on eventually-consistent state: `await().atMost(Duration).until(...)`.
- Do **not** use `Thread.sleep(...)` in tests — it is fragile and slow.
- Set a realistic but short timeout; document why if it exceeds 5 seconds.

## Integration Tests

- Use **Testcontainers** to start real external infrastructure (databases, message brokers).
- Annotate integration test classes with `@IT` suffix and place them in `src/integrationTest/java/`.
- Prefer one shared container per test class (via `@BeforeAll` static field) over per-test startup.
- Reset database state between tests using transactions or schema truncation, not container restart.

## Boundaries

- ✅ Always: mirror source package structure in test packages; use `@Nested` + `@DisplayName` everywhere; write GWT comments in every test body; ensure all tests pass before committing; aim for 80%+ line coverage on new code.
- ⚠️ Ask first: adding new test-scope dependencies; modifying shared test base classes or `@BeforeAll`/`@AfterAll` hooks that affect multiple suites.
- 🚫 Never: write flat test classes without `@Nested` grouping; omit `@DisplayName`; use `Thread.sleep()`; hardcode credentials; `@Disabled` without a documented reason.
