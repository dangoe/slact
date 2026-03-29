# Extension: Java Testing — slact

This file extends `.github/skills/java-testing.md` with project-specific testing context for the **slact** actor system project.

---

## Test Commands

```bash
./gradlew test                               # all unit tests (all modules)
./gradlew :core:test                         # unit tests for a specific module
./gradlew :core:test --tests "ClassName"     # single test class
./gradlew :core:test --tests "Class.method"  # single test method
./gradlew :persistence-jdbc:integrationTest  # integration tests (requires Docker)
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"  # perf tests (opt-in)
```

## Convention Plugins for Tests

- `slact.use-junit5-lib` — activates JUnit 5, AssertJ, Mockito for a module
- `slact.integration-test-lib` — creates the `integrationTest` source set + registers the `integrationTest` task

## JUnit Extension for Actor Tests

**Replace** `MockitoExtension` with `SlactTestContainerExtension` for any test that involves actors:

```java
@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("MyActor")
class MyActorTest {

    private SlactTestContainer container;  // injected by the extension

    @Test
    @DisplayName("when a Start message is sent, then actor transitions to active state")
    void whenStartSent_thenBecomesActive() {
        
        var actor = container.spawn(MyActor::new, "my-actor");
        var probe = container.spawn(CapturingActor::new, "probe");

        actor.send(new MyMessage.Start("hello"), probe);

        await().atMost(Duration.ofSeconds(2)).until(() ->
            probe.capturedMessages().stream().anyMatch(m -> m instanceof MyResponse.Active));
    }
}
```

## SlactTestContainer API

| Method | Purpose |
|--------|---------|
| `container.spawn(Creator, name)` | Create and start an actor, returns `ActorHandle<M>` |
| `handle.send(message)` | Send a message to an actor |
| `handle.send(message, sender)` | Send with explicit sender handle |
| `container.stop(handle)` | Stop an actor and await termination |

## Test Utility Actors (testkit module)

- **`CapturingActor<M>`** — captures all received messages; access via `capturedMessages()`; useful as a probe/spy
- **`FailingOnReceiveActor<M>`** — throws on receipt; useful to assert no unexpected messages reach an actor

```java
// Capturing probe pattern
var probe = container.spawn(CapturingActor<MyResponse>::new, "probe");
actor.send(new MyMessage.Query(), probe);
await().atMost(Duration.ofSeconds(2))
       .until(() -> !probe.capturedMessages().isEmpty());
assertThat(probe.capturedMessages()).hasSize(1);
```

## Async Assertions

Always use **Awaitility** for actor tests (actors are inherently async):

```java
await().atMost(Duration.ofSeconds(2)).until(() -> condition);
await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(x).isEqualTo(y));
```

Never use `Thread.sleep(...)`.

## Integration Tests (persistence-jdbc)

- Located in `src/integrationTest/java/`; class names end with `*IT`
- Use **Testcontainers** with a PostgreSQL container
- Activated via `slact.integration-test-lib` convention plugin
- Share one container per test class with `@BeforeAll` static setup
- Apply schema via Liquibase changesets (already configured in the module)

```java
@Testcontainers
@DisplayName("JdbcEventStore")
class JdbcEventStoreIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // ...
}
```

## Pre-Commit Verification

Before committing, always run both unit tests and integration tests for any module touched by the change:

```bash
./gradlew test                               # all unit tests (all modules)
./gradlew :persistence-jdbc:integrationTest  # integration tests — requires Docker
```

> If Docker is unavailable in the current environment, document this clearly in the commit message and ensure CI will catch integration test failures.

The `integrationTest` task is wired into the `check` lifecycle via the `slact.integration-test-lib` plugin, so `./gradlew check` runs both unit and integration tests for all applicable modules.

## Performance Tests

Performance tests live in `core` and are **opt-in**:

```bash
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"
```

They are skipped when `PERF_TEST` is not set. Do not gate normal CI on them.

## Test Naming Quick Reference

| Element | Convention | Example |
|---------|-----------|---------|
| Test class | `PascalCase` + `Test` | `ActorBehaviorTest` |
| Integration test class | `PascalCase` + `IT` | `JdbcEventStoreIT` |
| `@Nested` class | PascalCase scenario | `WhenActorReceivesStartMessage` |
| Test method | `whenAction_thenOutcome` | `whenStartSent_thenBecomesActive` |
| Probe actor variable | `probe` or `<role>Probe` | `responseProbe` |
