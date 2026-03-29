# Project Context: slact

**slact** is a Java 21 actor system — typed message passing, hierarchical supervision, behavioral switching, and event-sourced persistence. It is organized into modules: `core`, `testkit`, `persistence`, `persistence-jdbc`, and `build-logic` (Gradle convention plugins).

Root package: `de.dangoe.concurrent.slact`

## Module Overview

| Module | Purpose | Key convention plugins |
|--------|---------|------------------------|
| `core` | Actor runtime — `Actor<M>`, `SlactContainer`, handles, paths, patterns | `slact.java-lib` |
| `testkit` | Testing utilities — `SlactTestContainer`, `SlactTestContainerExtension`, capturing actors | `slact.java-lib` |
| `persistence` | Event sourcing abstraction — `EventStore`, `PersistentActor`, snapshotting | `slact.java-lib` |
| `persistence-jdbc` | JDBC-backed event store (PostgreSQL + HikariCP) | `slact.java-lib`, `slact.integration-test-lib` |
| `build-logic` | Gradle convention plugins shared across all modules | N/A |

## Key References

- Coding conventions: `CODING_STYLE.md`
- Development patterns & build commands: `.github/skills/java-development.extension.md`
- Testing utilities & test commands: `.github/skills/java-testing.extension.md`

## Quick Reference

```bash
./gradlew build                                         # compile + test all modules
./gradlew test                                          # all unit tests
./gradlew :core:test --tests "ClassName"                # single test class
./gradlew :core:test --tests "ClassName.method"         # single test method
./gradlew :persistence-jdbc:integrationTest             # integration tests (Docker required)
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"
```
