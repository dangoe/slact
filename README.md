# slact

A lightweight actor system implementation in Java 21 — built as a learning project to explore the actor model and concurrent message passing from scratch.

## Motivation

This is a **tinker project** created to learn about actor system internals: message dispatching, actor lifecycle management, hierarchical supervision, and behavioral switching. It is not intended for production use but rather as an educational exercise in building concurrency primitives.

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

| Module        | Description                                              |
|---------------|----------------------------------------------------------|
| `core`        | Main library — actor runtime, message handling, patterns |
| `testkit`     | Testing utilities — test container, JUnit 5 extension    |
| `build-logic` | Gradle convention plugins for shared build configuration |

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

## AI-Assisted Development

Parts of this codebase were created with the help of **AI agents** (GitHub Copilot). AI was primarily used to improve **test completeness** — generating test cases for edge cases, lifecycle scenarios, and message flow coverage. The core architecture and design decisions are human-driven. All AI generated code is reviewed by the author for correctness and maintainability.

## Tech Stack

- **Java 21** — records, sealed interfaces, pattern matching
- **Gradle 8.5** — with convention plugins and version catalogs
- **JUnit 5** — testing framework
- **AssertJ** — fluent assertions
- **Awaitility** — async testing support
- **SLF4J / Logback** — logging

## License

This project is licensed under the [MIT License](LICENSE).

