# Copilot Instructions for slact

## Project Overview

**slact** is a Java 21 actor system — typed message passing, hierarchical supervision, behavioral switching, and event-sourced persistence. It is organized into modules: `core`, `testkit`, `persistence`, `persistence-jdbc`, and `build-logic` (Gradle convention plugins).

For coding conventions see `CODING_STYLE.md`.  
For build/test details and actor patterns see `.github/skills/java-development.extension.md`.  
For testing utilities and test commands see `.github/skills/java-testing.extension.md`.

---

## Agent Routing

Use the right agent for your task:

| Task | Agent | Command |
|------|-------|---------|
| Plan a feature, bug fix, or refactor | **Planner** | `/agent planner` |
| Run the full plan → implement → test → review workflow | **Task Workflow Orchestrator** | `/agent task-workflow-orchestrator` |
| Implement a feature or fix from an existing plan | **Developer** | `/agent developer` |
| Write, fix, or improve tests | **Software testing expert (Java)** | `/agent testing-expert` |
| Review staged or committed changes | **Reviewer** | `/agent reviewer` |

All agents are project-agnostic. Project-specific context is injected automatically via skill extensions (`.github/skills/*.extension.md`).

---

## Quick Reference

```bash
./gradlew build                                    # compile + test all modules
./gradlew test                                     # all unit tests
./gradlew :core:test --tests "ClassName"           # single test class
./gradlew :core:test --tests "ClassName.method"    # single test method
./gradlew :persistence-jdbc:integrationTest        # integration tests (Docker required)
PERF_TEST=true ./gradlew :core:test --tests "*ActorPerformanceTest"
```
