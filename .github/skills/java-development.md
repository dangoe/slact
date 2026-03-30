# Skill: Java Development

You are an expert Java developer. Apply all guidelines in this skill when writing, reviewing, or
reasoning about Java source code.

> **Extension:** Before proceeding, check whether `.github/skills/java-development.extension.md`
> exists in the repository root. If it does, read it and incorporate its project-specific context
> alongside the guidelines below.

---

## Language & Idioms (Java 21)

- Prefer **records** for immutable data carriers; avoid manual getters/setters/equals/hashCode for
  value objects.
- Use **sealed interfaces** with `permits` to model closed type hierarchies (algebraic types).
- Use **pattern matching** (`instanceof` patterns, `switch` expressions) instead of chains of
  `if/else instanceof`.
- Use **text blocks** for multi-line string literals.
- Prefer `var` for local variables only when the type is obvious from the right-hand side.
- Use the **Java Module System** (`module-info.java`) to declare explicit dependencies and exports;
  never rely on classpath leakage.

## Code Organisation

- One top-level public type per file; file name must match the type name.
- Package names: all lowercase, no underscores, reflect domain hierarchy.
- Group types by domain/feature, not by layer (prefer `actor.supervision` over `services`).
- Keep `internal` / `impl` packages for non-exported implementation detail; never export them from
  `module-info.java`.
- Prefer **composition over inheritance**; use abstract classes only when shared behaviour truly
  cannot be expressed via interfaces + delegation.

## Nullability

- Annotate **every** method parameter and return type with `@NotNull` or `@Nullable` (from
  `org.jetbrains.annotations`).
- Never return `null` from a public API; use `Optional<T>` for absent values, or throw an explicit
  exception.
- Validate `@NotNull` parameters with a guard at the top of the method when the annotation alone is
  insufficient at runtime.

## Error Handling

- Define a typed exception hierarchy rooted at a project-specific base exception.
- Throw **unchecked exceptions** for programming errors (illegal state, violated preconditions).
- Use **checked exceptions** only when the caller can meaningfully recover.
- Never swallow exceptions silently; log or rethrow with context.
- Avoid generic `RuntimeException` and `Exception` — use or create a specific subtype.

## Immutability & Thread Safety

- Make fields `final` by default; mutate only when necessary.
- Prefer immutable collections (`List.of`, `Map.of`, `Collections.unmodifiableList`).
- Document thread-safety guarantees (or lack thereof) with `@GuardedBy` / inline comments when a
  class is designed for concurrent use.
- Avoid shared mutable state; pass state explicitly through method parameters.

## Patterns

- Use the **Builder pattern** for objects with more than three optional constructor parameters.
- Use **factory methods** (`of(...)`, `from(...)`, `create(...)`) instead of constructors for named
  construction semantics.
- Use **strategy / functional interfaces** (`Function`, `Predicate`, custom `@FunctionalInterface`)
  to parameterise behaviour.
- Apply the **Open/Closed Principle**: extend behaviour by composition/injection, not by modifying
  existing classes.

## Dependencies & Build

- Declare all dependencies in the build system's version catalog or BOM; never hardcode version
  strings inline.
- Mark compile-only annotations (`@NotNull`, `@Nullable`) as `compileOnly`/`annotationProcessor`
  scope, not runtime.
- Prefer `api` over `implementation` only when downstream consumers genuinely need the transitive
  type on their compile classpath.
- Keep `build.gradle.kts` / `pom.xml` thin; extract shared configuration into convention plugins or
  parent POMs.

## Git Workflow

Every (sub-)task must follow this cycle — **Plan → Act → Build & Test → Commit → Push**.

### Branch Naming

Create a dedicated branch before starting any work. Never commit directly to `main`.

| Prefix      | Use for                                       |
|-------------|-----------------------------------------------|
| `feat/`     | New features                                  |
| `fix/`      | Bug fixes                                     |
| `refactor/` | Refactoring without behaviour change          |
| `test/`     | Test-only changes                             |
| `chore/`    | Build, tooling, dependency, or config updates |
| `docs/`     | Documentation only                            |

Pattern: `{prefix}/{short-kebab-description}`  
Examples: `feat/actor-supervision-timeout`, `fix/mailbox-overflow`, `chore/upgrade-junit5`

```bash
git checkout -b feat/short-description   # create and switch to the branch
git push -u origin feat/short-description
```

### Commit Messages (Conventional Commits)

```
{type}({optional scope}): {short description}

{optional body — explain why, not what, wrapped at 72 chars}
```

Types match branch prefixes: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`.  
Subject line: imperative mood, ≤ 72 characters, no trailing period.

Examples:

- `feat(core): add supervision timeout to actor context`
- `fix(persistence): handle concurrent write exception on retry`
- `test(core): add edge cases for actor behaviour switching`

### Task Cycle

```bash
# 1. Plan  — understand what needs to change (no code yet)
# 2. Act   — implement using edit/create tools
# 3. Build — verify nothing is broken
./gradlew build

# 4. Commit — stage and commit (only after a green build)
git add -p
git commit -m "feat(scope): short description"

# 5. Push
git push
```

- Never commit with a failing build or failing tests.
- Prefer small, focused commits — one logical change per commit.
- Each sub-task (todo) should produce its own commit.

---

# Javadoc Formatting Guidelines

## General Rules

1. **Summary sentence** — The first sentence of every Javadoc block must start with an uppercase
   letter and end with a period. This applies to classes, interfaces, methods, and fields.
2. **`@param` descriptions** — Must start with a lowercase letter. Use a short phrase, no trailing
   period. Full sentences are permitted but must be properly punctuated.
3. **`@return` descriptions** — Must start with a lowercase letter. Use a short phrase, no trailing
   period. Full sentences are permitted but must be properly punctuated.
4. **`@throws` / `@exception` descriptions** — Must start with a lowercase letter. Typically phrased
   as a condition: *"if the argument is null"*. No trailing period unless a full sentence.
5. **Type names and proper nouns** — Always capitalised regardless of position (e.g. `String`,
   `IOException`, `MyService`).
6. **Inline code references** — Always use `{@code ...}` for values, types, and expressions (e.g.
   `{@code null}`, `{@code true}`, `{@code 0}`). Never use backticks.
7. **Conciseness** — Javadoc comments must be concise:
    - **Tag descriptions** (`@param`, `@return`, `@throws`) — one short phrase or at most one
      sentence.
    - **Method and class descriptions** — keep to a maximum of 3 sentences.
    - **Exception** — central API classes, facade methods, and public entry points may include
      additional detail such as usage examples (`{@code ...}` blocks), preconditions, or behavioural
      notes where this genuinely aids the caller.

## Refactoring Guidelines

When applying the above rules to an existing codebase, follow these instructions:

1. **Scope** — Apply corrections uniformly across all classes in scope. Do not mix styles within the
   same file or package.
2. **Summary sentences** — Ensure the first sentence of every Javadoc block starts with an uppercase
   letter and ends with a period. Correct any violations.
3. **Tag descriptions** — Review all `@param`, `@return`, and `@throws` descriptions and correct the
   first letter to lowercase where needed. Add or remove trailing periods to match the phrase vs.
   full-sentence rule.
4. **Inline code references** — Replace any backtick-style or plain-text references to code values,
   types, or expressions with `{@code ...}`.
5. **Conciseness** — Trim overly verbose descriptions to comply with the conciseness rule. Tag
   descriptions must be reduced to a single phrase or sentence. Method and class descriptions must
   not exceed 3 sentences unless the class or method is a central part of the API, in which case
   additional detail and examples are permitted.
6. **Preserve meaning** — Do not alter the meaning or content of existing documentation. Only
   correct capitalisation, punctuation, code reference formatting, and verbosity.
7. **No new documentation** — Do not add Javadoc where it does not already exist. This is a
   formatting refactoring, not a documentation task.

## Clean Code Checklist

- Methods: ≤ 20 lines, single responsibility, verb-phrase names.
- Classes: ≤ 300 lines (excluding Javadoc); split when cohesion is low.
- No magic numbers or strings — use named constants or enums.
- No `System.out.println` in production code — use a logging façade (`SLF4J` or equivalent).
