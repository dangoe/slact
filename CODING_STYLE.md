# Coding Style Guidelines for slact

## Java Language Features
- Use records, sealed interfaces, and modules for clarity and immutability
- Explicitly annotate nullability (`@NotNull`, `@Nullable`) on all method parameters and return types

## Actor Model Conventions
- Message types: sealed interfaces and records
- Typed actors (`Actor<M>`) and actor handles for message passing
- Switch behaviors with `behaveAs()` and `behaveAsDefault()`; use `onStart`/`onStop` for initialization/cleanup
- Hierarchical actor supervision: spawn child actors via context

## Testing

### Test Types and Source Sets
- **Unit tests**: live in `src/test/java`, class names end with `*Test` (e.g. `ActorBehaviorTest`)
  - Run with `./gradlew test`
  - Applied via the `slact.use-junit5-lib` convention plugin
- **Integration tests**: live in `src/integrationTest/java`, class names end with `*IT` (e.g. `JdbcPersistentActorIT`)
  - Run with `./gradlew integrationTest`
  - Applied via the `slact.integration-test-lib` convention plugin
  - Use when external infrastructure (databases, containers, etc.) is required

### Test Style
- Use JUnit 5 with `SlactTestContainerExtension` for actor tests
- Group tests with nested classes for scenario clarity
- Cover lifecycle hooks, message flows, edge cases, and domain invariants

## Build and Dependency Management
- Use Gradle convention plugins from `build-logic` for shared configuration
- Add dependencies to `gradle/libs.versions.toml`, not inline

## General
- Write clear, maintainable, modular code
- Document architectural decisions and complex flows
- Follow project conventions for naming, structure, and organization
