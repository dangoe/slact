# slact

[![CI](https://github.com/dangoe/slacktors/actions/workflows/ci.yml/badge.svg)](https://github.com/dangoe/slacktors/actions/workflows/ci.yml)
[![License: MIT OR Apache-2.0](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue.svg)](LICENSE-MIT)

A lightweight actor system implementation in Java 21 — built as a learning project to explore the actor model and concurrent message passing from scratch.

## Motivation

This project began as a **tinker project** to learn actor system internals — message dispatching, actor lifecycle management, hierarchical supervision, and behavioral switching. Over time it has matured into a compact, well-tested runtime with a practical feature set; publishing artifacts may now be of interest for others who want a lightweight Java 21 actor toolkit or a learning resource.

## Current State

> **Note:** slact is under active development as a learning project. APIs, modules, and
> persistence surfaces may change between versions without prior deprecation.

## Features

- **Typed actors** — actors are generic (`Actor<M>`) and process only their declared message type
- **Message passing** — actors communicate exclusively through typed, asynchronous messages
- **Behavioral switching** — actors can change their message handling at runtime via `behaveAs()` / `behaveAsDefault()`
- **Lifecycle hooks** — `onStart()` and `onStop()` callbacks for initialization and cleanup
- **Hierarchical actor spawning** — actors can spawn child actors through their context
- **Actor handles** — type-safe references (`ActorHandle<M>`) for addressing and sending messages
- **Actor paths** — hierarchical addressing for actors in the system
- **Request-response** — built-in support for `respondWith()` and future-based responses
- **Routing patterns** — included `RoutingActor` with round-robin strategy for work distribution
- **Future piping** — pipe `Future` results into actor mailboxes
- **Testkit** — dedicated module with `SlactTestContainer` and a JUnit 5 extension for actor testing
- **Java Module System** — explicit module boundaries via `module-info.java`

## Project Structure

| Module                | Path                  | Description                                                         |
| --------------------- | --------------------- | ------------------------------------------------------------------- |
| `core`                | `core`                | Main library — actor runtime, message handling, and patterns        |
| `testkit`             | `testkit`             | Testing utilities — test container and JUnit 5 extension            |
| `persistence`         | `persistence`         | Event sourcing abstraction — event stores and persistent actors     |
| `persistence-jdbc`    | `persistence-jdbc`    | JDBC-backed persistence implementation with PostgreSQL and HikariCP |
| `persistence-testkit` | `persistence-testkit` | Shared persistence testing specs and reusable contract coverage     |
| `build-logic`         | `build-logic`         | Gradle convention plugins for shared build configuration            |

## Quick Start

### Prerequisites

- Java 21+
- Gradle 8.5+ (wrapper included)

### Build

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Example

```java
// Define a message type
sealed interface Greeting { }
record Hello(String name) implements Greeting { }

// Define an actor
class GreeterActor extends Actor<Greeting> {
    @Override
    public void onMessage(Greeting message) {
        if (message instanceof Hello(String name)) {
            System.out.println("Hello, " + name + "!");
        }
    }
}

// Bootstrap the actor system
try (var container = new SlactContainerBuilder().build()) {
    var greeter = container.spawn("greeter", GreeterActor::new);
    greeter.send(new Hello("World"));
}
```

## Development

```bash
./gradlew build
./gradlew test
./gradlew :core:test --tests "ClassName"
./gradlew :core:test --tests "ClassName.method"
./gradlew :persistence-jdbc:integrationTest
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"
```

Integration tests require Docker.

## Tech Stack

- **Java 21** — records, sealed interfaces, pattern matching
- **Gradle 8.5** — with convention plugins and version catalogs
- **JUnit 5** — testing framework
- **AssertJ** — fluent assertions
- **Awaitility** — async testing support
- **SLF4J / Logback** — logging

## Roadmap

The following features are not yet implemented but will be added when of interest:

- **Supervision strategies** — configurable failure handling policies (restart, stop, escalate) for parent-child actor hierarchies
- **Backpressure / bounded mailboxes** — mailbox capacity limits with overflow strategies (e.g. drop, reject) to prevent unbounded memory growth
- **Dead letter handling** — capture and surface messages sent to stopped or non-existent actors for debugging and monitoring
- **Configurable timeouts** — user-defined timeouts for request-response, future piping, and container shutdown
- **Observability** — hooks for metrics (mailbox depth, message throughput, processing latency) and structured diagnostics

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for branching strategy,
development workflow, and guidelines.

## License

Dual-licensed under [MIT](LICENSE-MIT) or [Apache-2.0](LICENSE-APACHE), at your option.

Copyright © 2024-2026 Daniel Götten
