# Copilot Instructions for slact

## General Information

This repository is a Java project using Gradle and the actor model. It is organized into modules (`core`, `testkit`, `build-logic`).

### Build, Test, and Lint

- Build all modules: `./gradlew build`
- Run all tests: `./gradlew test`
- Run tests for a specific module: `./gradlew :core:test`, `./gradlew :testkit:test`
- Run a single test class: `./gradlew :core:test --tests "<TestClassName>"`
- Run a single test method: `./gradlew :core:test --tests "<TestClassName>.<methodName>"`
- Enable performance tests: `PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"`

### High-Level Architecture

- Modular structure: `core` (main library), `testkit` (testing utilities), `build-logic` (Gradle convention plugins)
- Java module system is used for explicit package exports
- Actor model: actors communicate via typed message passing and hierarchical supervision

### Agent Integration

- Use AGENTS.md to define custom agent roles for Copilot CLI sub-agent chains
- Use `/agent`, `/fleet`, and `/tasks` commands to orchestrate agent workflows

For detailed coding conventions and project-specific style, see CODING_STYLE.md.
