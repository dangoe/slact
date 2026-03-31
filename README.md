# slact

`slact` is a Java 21 actor system with typed message passing, hierarchical supervision, behavioral switching, and event-sourced persistence extensions.

Originally started as a learning project, it now contains a broader multi-module setup for actor runtime, persistence, and agentic-memory integrations.

## Core capabilities

- Typed actors via `Actor<M>` and `ActorHandle<M>`
- Asynchronous message passing and request/response interactions
- Behavior switching with `behaveAs(...)` / `behaveAsDefault()`
- Actor lifecycle hooks (`onStart`, `onStop`)
- Hierarchical actor paths and child spawning
- Future piping into actor mailboxes
- Routing utilities (including round-robin routing actor patterns)
- Persistence abstraction for event-sourced actors
- Testkits for actor and persistence-heavy testing
- Agentic memory orchestration modules (core, Neo4j, MCP, demo)

## Project modules

| Module | Purpose |
|---|---|
| `core` | Actor runtime (`Actor`, container, paths, patterns) |
| `testkit` | Testing support (`SlactTestContainer`, JUnit 5 extension) |
| `persistence-core` | Event-sourcing abstractions (`EventStore`, `PersistentActor`) |
| `persistence-jdbc` | JDBC-based persistence implementation (PostgreSQL + HikariCP) |
| `persistence-testkit` | Testing utilities/specs for persistence modules |
| `agentic-ai-memory-core` | Memory orchestration actors and ports |
| `agentic-ai-memory-neo4j` | Neo4j-backed memory integration |
| `agentic-ai-memory-mcp` | MCP integration for memory features |
| `agentic-ai-memory-testkit` | Test helpers for agentic memory modules |
| `agentic-ai-memory-demo` | CLI demo application |
| `build-logic` | Shared Gradle convention plugins |

## Prerequisites

- Java 21
- Gradle wrapper (`./gradlew`) is included
- Docker (for integration tests using Testcontainers)

## Build and test

```bash
./gradlew build
./gradlew test
./gradlew :core:test
./gradlew :core:test --tests "ClassName"
./gradlew :core:test --tests "ClassName.method"
./gradlew :persistence-jdbc:integrationTest
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"
```

## Run the memory demo

```bash
./gradlew :agentic-ai-memory-demo:run
```

Main class: `de.dangoe.concurrent.slact.memory.demo.MemoryDemoCli`

## Minimal actor example

```java
sealed interface Greeting permits Greeting.Hello {
  record Hello(String name) implements Greeting {}
}

final class GreeterActor extends Actor<Greeting> {
  @Override
  public void onMessage(final Greeting message) {
    switch (message) {
      case Greeting.Hello hello -> System.out.println("Hello, " + hello.name() + "!");
    }
  }
}

try (var container = new SlactContainerBuilder().build()) {
  var greeter = container.spawn("greeter", GreeterActor::new);
  container.send(new Greeting.Hello("World")).to(greeter);
}
```

## AI-assisted development

Parts of this codebase were developed with support from AI agents (including GitHub Copilot).

AI is used as an engineering aid (for example for implementation drafts, refactor support, and test case generation), while architecture decisions and final code quality remain under human ownership.

All AI-generated or AI-assisted changes are reviewed manually before being accepted.

## Tech stack

- Java 21
- Gradle with convention plugins and version catalog
- JUnit 5, AssertJ, Awaitility, Mockito
- SLF4J / Logback
- Testcontainers (integration tests)
- Neo4j Java Driver (agentic memory integration)

## License

This project is licensed under the [MIT License](LICENSE).
