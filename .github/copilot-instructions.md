# Copilot Instructions

## Agent Workflow

This is a Java/Gradle project. Use the following agents for development tasks:

| Task | Agent | Command |
|------|-------|---------|
| Plan a feature, bug fix, or refactor | **Planner** | `/agent planner` |
| Run the full plan → implement → test → review workflow | **Task Workflow Orchestrator** | `/agent task-workflow-orchestrator` |
| Implement a feature or fix from an existing plan | **Developer** | `/agent developer` |
| Write, fix, or improve tests | **Software testing expert (Java)** | `/agent testing-expert` |
| Review staged or committed changes | **Reviewer** | `/agent reviewer` |

All agents load project-specific context automatically from `.github/skills/*.extension.md` alongside the base skill guidelines.
