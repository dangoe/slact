# Contributing to slact

Contributions are welcome! Please open an issue to discuss significant changes before
submitting a pull request.

---

## Branching Strategy

slact follows a trunk-based branching model with four branch types. All branches are
short-lived except `main`.

### `main`

The single long-lived branch. `main` always reflects the latest released or
release-ready state. All work is integrated here via pull requests.

### `feat/<name>`

Feature branches for new functionality or enhancements.

- Branch from `main`
- Merge back into `main` via pull request
- Delete after merge
- Example: `feat/persistent-actor-recovery`, `feat/router-supervision`

### `fix/<name>`

Bug fix branches for correcting defects.

- Branch from `main`
- Merge back into `main` via pull request
- Delete after merge
- Example: `fix/mailbox-ordering`, `fix/jdbc-snapshot-recovery`

### `release/<version>`

Short-lived branches for release stabilization. Used when a release needs final
adjustments without blocking ongoing feature work on `main`.

- Branch from `main`
- Only release-related commits (version bumps, changelog, critical fixes)
- Merge back into `main` via pull request, then tag the merge commit
- Delete after merge
- Example: `release/1.0.0`, `release/1.1.0`

---

## Commit Messages

Write concise commit messages that focus on the _why_ rather than the _what_. Use
imperative mood (for example, "Add CI workflow" instead of "Added CI workflow").

---

## Development Workflow

```bash
# Create a branch
git checkout -b feat/my-change

# Build and run the standard checks
./gradlew build
./gradlew test

# Module-specific test runs
./gradlew :core:test --tests "ClassName"
./gradlew :core:test --tests "ClassName.method"

# Integration tests (requires Docker)
./gradlew :persistence-jdbc:integrationTest

# Optional performance test
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"
```

See the [README](README.md#development) for the main development commands.

---

## License

By contributing, you agree that your contributions will be dual-licensed under
[MIT](LICENSE-MIT) or [Apache-2.0](LICENSE-APACHE), at your option.
