# Extension: Java Development — slact

This file extends `.github/skills/java-development.md` with project-specific context for the **slact** actor system project.

---

## Module Structure

| Module | Purpose | Key convention plugins |
|--------|---------|------------------------|
| `core` | Actor runtime — `Actor<M>`, `SlactContainer`, handles, paths, patterns | `slact.java-lib` |
| `testkit` | Testing utilities — `SlactTestContainer`, `SlactTestContainerExtension`, capturing actors | `slact.java-lib` |
| `persistence` | Event sourcing abstraction — `EventStore`, `PersistentActor`, snapshotting | `slact.java-lib` |
| `persistence-jdbc` | JDBC-backed event store (PostgreSQL + HikariCP) | `slact.java-lib`, `slact.integration-test-lib` |
| `build-logic` | Gradle convention plugins shared across all modules | N/A |

Root package: `de.dangoe.concurrent.slact`

## Build & Test Commands

```bash
./gradlew build                          # compile + test all modules
./gradlew test                           # run all unit tests
./gradlew :core:test                     # unit tests for a single module
./gradlew :core:test --tests "ClassName" # run a single test class
./gradlew :core:test --tests "ClassName.method" # run a single test method
./gradlew :persistence-jdbc:integrationTest  # integration tests (requires Docker)
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"  # opt-in perf tests
```

## Gradle Convention Plugins

| Plugin | What it provides |
|--------|-----------------|
| `slact.base-lib` | Java 21 toolchain, group/version, Javadoc/sources jars, `@NotNull`/`@Nullable` annotations |
| `slact.java-lib` | Applies `base-lib` + `use-junit5-lib` |
| `slact.use-junit5-lib` | JUnit 5, AssertJ, Mockito, `useJUnitPlatform()` |
| `slact.integration-test-lib` | `integrationTest` source set + task wired to `check` |

Always apply a convention plugin rather than configuring Java / test settings inline.

## Version Catalog

All dependency versions live in `gradle/libs.versions.toml`. **Never** hardcode version strings in `build.gradle.kts`. Reference dependencies as `libs.<alias>`.

```kotlin
// ✅ correct
implementation(libs.slf4j.api)

// ❌ wrong
implementation("org.slf4j:slf4j-api:2.0.17")
```

## Java Module System

Each module has a `module-info.java`. Export only public API packages; keep `internal` packages unexported.

```java
module de.dangoe.concurrent.slact {
    exports de.dangoe.concurrent.slact.core;
    exports de.dangoe.concurrent.slact.core.exception;
    // internal.* is NOT exported
    requires org.slf4j;
    requires org.jetbrains.annotations;
}
```

## Actor Model Patterns

### Core types
- `Actor<M>` — abstract base; `M` is the message type (a sealed interface + records)
- `ActorHandle<M>` — typed reference to send messages to an actor
- `ActorPath` — hierarchical addressing (e.g. `/user/parent/child`)
- `SlactContainer` / `SlactContainerBuilder` — runtime container; implements `AutoCloseable`

### Message types
Define message types as **sealed interfaces + records**:
```java
sealed interface MyMessage permits MyMessage.Start, MyMessage.Stop {
    record Start(@NotNull String payload) implements MyMessage {}
    record Stop() implements MyMessage {}
}
```

### Behaviour switching
```java
@Override
protected void receiveMessage(@NotNull MyMessage message) {
    switch (message) {
        case MyMessage.Start s -> { behaveAs(this::activeMode); }
        case MyMessage.Stop  _ -> { behaveAsDefault(); }
    }
}
```

### Lifecycle hooks
```java
@Override protected void onStart() { /* initialise resources */ }
@Override protected void onStop() { /* release resources */ }
```

### Spawning child actors
```java
ActorHandle<ChildMessage> child = context().spawn(ChildActor::new, "child-name");
```

### Request / response
```java
context().respondWith(result);         // reply to sender
context().pipeFuture(someCompletableFuture); // pipe future result into mailbox
```

## Coding Style Quick Reference

- Use records for message types and value objects.
- Use sealed interfaces for closed type hierarchies (messages, decisions).
- Annotate all parameters and return types with `@NotNull` / `@Nullable`.
- `internal` packages are never exported from `module-info.java`.
- No magic strings — define constants or use enums.
- No `System.out.println` — use `ActorLogger` (wraps SLF4J) inside actors.
