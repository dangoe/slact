# JVM Language Migration Evaluation

## Context

This document evaluates whether migrating the `core` module from Java 21 to Kotlin, Scala, or
Clojure would be beneficial. The key constraints are:

- **Lean dependency footprint** — slact should remain as thin as possible with only the most
  essential dependencies.
- **Java API** — any JVM-alternative implementation must still expose a first-class Java API for
  downstream consumers.
- **Actor model suitability** — the target language should fit naturally to the actor model patterns
  used in the core.

---

## Baseline: Current Java 21 State

The current implementation leverages modern Java features extensively:

| Feature                         | Usage in slact                                                               |
|---------------------------------|------------------------------------------------------------------------------|
| Records                         | `SimpleRoutingRequest`, `ActorPath.Element` construction                     |
| Sealed interfaces               | `MailboxItem` hierarchy, message type contracts                              |
| Pattern matching (`switch`)     | `ReadyActorLogic.processMailboxItem`, `StoppingActorLogic.processMailboxItem` |
| `var` type inference            | Widespread use in method bodies                                              |
| `@NotNull`/`@Nullable`          | All parameters and return types annotated via Jetbrains annotations          |
| Java Module System              | Explicit `module-info.java` with controlled package exports                  |
| `CompletableFuture`             | Request-response pattern, stop coordination                                  |
| `ScheduledExecutorService`      | Thread-pool-based mailbox dispatching                                        |
| `AtomicBoolean`/`AtomicReference` | Lock-free concurrency in `ActorWrapper`                                    |

**Runtime dependencies** (`core` module):

| Artifact                    | Purpose                             | Approximate size |
|-----------------------------|-------------------------------------|------------------|
| `org.slf4j:slf4j-api`       | Logging abstraction                 | ~65 KB           |
| `org.jetbrains:annotations` | Nullability annotations             | ~30 KB           |

Total additional runtime dependency weight: **~95 KB**.

---

## Kotlin

### Language Fit

Kotlin runs on the JVM, produces standard class files, and interoperates bidirectionally with Java
with near-zero friction. It shares many concepts with modern Java 21 but goes further in several
areas relevant to slact.

| Area                   | Java 21                                        | Kotlin                                              |
|------------------------|------------------------------------------------|-----------------------------------------------------|
| Null safety            | External `@NotNull`/`@Nullable` annotations    | Built into the type system (`T` vs `T?`)            |
| Data carriers          | Records (immutable, no inheritance)            | Data classes (mutable allowed, can be sealed)       |
| Sealed hierarchies     | Sealed interfaces/classes                      | Sealed classes/interfaces (richer pattern matching) |
| Pattern matching       | `switch` expressions (JEP 441)                 | `when` expressions (more powerful, exhaustive)      |
| Concurrency            | Thread pool, virtual threads (JEP 444)         | Coroutines (structured concurrency, `Flow`)         |
| Boilerplate            | Primary constructors via records only          | Primary constructors on all classes                 |
| Extension methods      | Not available                                  | Extension functions on any type                     |

**Impact on slact core:**

- `ActorWrapper` has many annotated fields and long constructors; Kotlin primary constructors and
  null safety would reduce noise significantly.
- The `ActorLogic` internal interface hierarchy maps directly to Kotlin sealed classes.
- Coroutines (`kotlinx-coroutines`) could replace the thread-pool dispatcher with lightweight
  structured concurrency — but this is an _optional_ dependency, not forced.
- The `ScheduledExecutor` abstraction could leverage Kotlin `delay` / `launch` natively.

### Java API Impact

Most Kotlin constructs are callable from Java without modification:

- Regular classes and functions are fully transparent.
- Extension functions become static utility methods.
- Data classes expose component accessors and `copy()`.
- `suspend` functions are **not directly callable** from Java; they require a coroutine context.
  Any suspend-based internal API would need non-suspend wrapper overloads if exposed in the public
  API — this is the main Java-interop constraint when using coroutines.
- `Unit` return type maps to `void` in Java, which is transparent.
- SAM (Single Abstract Method) conversions work in both directions.

**Verdict:** A well-designed Kotlin implementation can maintain a 100% Java-compatible public API
without separate wrapper code, provided coroutines are kept in the internal layer only.

### Dependency Cost

| Artifact                                | Required             | Approximate size |
|-----------------------------------------|----------------------|------------------|
| `org.jetbrains.kotlin:kotlin-stdlib`    | Always               | ~1.8 MB          |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | Optional (but large benefit) | ~1 MB |

Adding Kotlin-stdlib increases the library dependency footprint by roughly **20×** compared to the
current baseline. For a library targeting minimal footprint, this is a meaningful cost.

However, it is worth noting that `kotlin-stdlib` replaces `org.jetbrains:annotations` (both come
from the same vendor) and subsumes many common Java utilities, so the _net_ addition is
`kotlin-stdlib` minus what it replaces.

### Module System

Kotlin supports the Java Module System but with caveats: modules are defined in
`module-info.java` (not `.kt`), and there are known limitations with mixed Kotlin/Java module
compilation. The `module-info.java` can be retained as-is; only the source files switch language.
Gradle with the `kotlin-jvm` plugin handles this correctly.

### Summary

| Criterion                 | Rating  | Notes                                                               |
|---------------------------|---------|---------------------------------------------------------------------|
| Actor model fit           | ★★★★★   | Coroutines are ideal; sealed classes map directly                   |
| Lean dependency footprint | ★★★☆☆   | `kotlin-stdlib` is ~1.8 MB; coroutines add another ~1 MB           |
| Java API quality          | ★★★★★   | Transparent for non-suspend code; wrappers needed for suspend APIs  |
| Gradual migration         | ★★★★★   | Kotlin and Java files can coexist in the same module                |
| Learning curve            | ★★★★☆   | Java developers adapt quickly; coroutines require study             |

---

## Scala

### Language Fit

Scala 3 (the current major version) is a powerful JVM language with an advanced type system,
exhaustive pattern matching, and a functional programming heritage. Akka (now Pekko), the most
widely used JVM actor system, is written in Scala.

| Area                   | Java 21                                       | Scala 3                                           |
|------------------------|-----------------------------------------------|---------------------------------------------------|
| Null safety            | `@NotNull`/`@Nullable` annotations             | `Option[T]` for optional values; type system based |
| Data carriers          | Records                                       | Case classes                                      |
| Sealed hierarchies     | Sealed interfaces/classes                     | Sealed traits / enums (very powerful)             |
| Pattern matching       | `switch` expressions                          | `match` expressions (exhaustive, deep, guards)    |
| Concurrency            | Thread pool, virtual threads                  | Futures, Akka actors (separate dependency)        |
| Type system            | Generics + wildcards                          | Higher-kinded types, type classes, union types    |
| Functional style       | Streams, Lambdas                              | First-class FP: for-comprehensions, monads, etc.  |

**Impact on slact core:**

- The sealed `MailboxItem` hierarchy, `ActorLogic`, and message type hierarchies benefit from
  Scala's more expressive `match` and sealed traits.
- Scala's type system would allow more precise modelling of the actor's generic message type
  constraints.
- The concurrency model in slact does not need Akka; plain Scala `Future` or Cats Effect IO
  could replace `CompletableFuture`.

### Java API Impact

Calling Scala from Java is significantly more complex than calling Kotlin from Java:

- **Traits with default implementations** compile to interfaces + companion classes; calling from
  Java requires care.
- **Case classes** expose `apply()`/`unapply()` via companion objects, which are not standard Java
  constructors; Java callers must use `.apply(...)` or generated constructors.
- **Implicit parameters / context parameters (Scala 3 `given`/`using`)** are invisible to Java
  callers; APIs using them cannot be consumed from Java without wrappers.
- **Higher-kinded types, union types, type aliases** are not representable in Java generics; they
  require erasure-based workarounds.
- **Scala collections** are not `java.util` collections; explicit conversion (`asJava`, `asScala`)
  is needed.
- The public API would need a **dedicated Java facade layer** (separate classes, no Scala-specific
  types exposed), which is a non-trivial ongoing maintenance burden.

**Verdict:** A Scala implementation requires a deliberate, separately maintained Java API wrapper
module. Without it, Java consumers face a hostile API surface.

### Dependency Cost

| Artifact                       | Required | Approximate size |
|--------------------------------|----------|------------------|
| `org.scala-lang:scala3-library` | Always  | ~5.5 MB          |
| Cats Effect / ZIO (optional)   | Optional | ~3–8 MB          |

The Scala 3 standard library adds **~60× the current baseline** in dependency weight. For a lean
library, this is a very significant cost with no straightforward mitigation.

### Module System

Scala 3 does not integrate natively with the Java Module System. Using `module-info.java` in a
mixed Scala/Java module requires the `ModuleInfoPlugin` Gradle plugin and a build setup that is
noticeably more complex than pure Java or Kotlin.

### Summary

| Criterion                 | Rating  | Notes                                                                 |
|---------------------------|---------|-----------------------------------------------------------------------|
| Actor model fit           | ★★★★★   | Excellent; Akka/Pekko heritage; expressive types                      |
| Lean dependency footprint | ★★☆☆☆   | `scala3-library` is ~5.5 MB, much heavier than Kotlin                 |
| Java API quality          | ★★☆☆☆   | Requires a separately maintained Java facade; high ongoing overhead   |
| Gradual migration         | ★★★☆☆   | Possible but build complexity is high; Scala and Java interop is work  |
| Learning curve            | ★★☆☆☆   | Steep; Scala idioms and type system differ significantly from Java     |

---

## Clojure

### Language Fit

Clojure is a Lisp dialect on the JVM designed around immutability, software transactional memory
(STM), and functional programming. It has native `agent` abstractions that are conceptually similar
to the actor model.

| Area                   | Java 21                   | Clojure                                              |
|------------------------|---------------------------|------------------------------------------------------|
| Null safety            | `@NotNull`/`@Nullable`    | No static types; `nil` is the default null           |
| Data carriers          | Records (static types)    | Maps/records (dynamic, no compile-time checking)     |
| Sealed hierarchies     | Sealed types              | Protocols (runtime dispatch)                         |
| Pattern matching       | `switch` expressions      | `cond`, `case`, `core.match` (library)               |
| Concurrency            | Thread pool, virtual threads | STM (`ref`), `atom`, `agent`, `core.async`        |
| Type safety            | Static generics           | Fully dynamic; optional `clojure.spec` validation    |
| Syntax                 | Standard Java             | LISP (S-expressions)                                 |

**Impact on slact core:**

- Clojure `agent` semantics are action-based (sequential execution per agent), which is similar to
  an actor mailbox, but the programming model is quite different.
- There is no native compile-time type safety; `clojure.spec` provides runtime validation only.
- The `ActorWrapper` generic type safety (`ActorHandle<M>`, `Actor<M>`) would be lost.

### Java API Impact

Clojure can be compiled and packaged for Java consumption using `gen-class` or `definterface`, but:

- Java callers work through generated class stubs or `clojure.lang.IFn` wrappers, which are
  unintuitive.
- Type information is erased at the Clojure level; Java consumers receive `Object` in most cases.
- The Clojure runtime (`clojure.jar`) must always be on the classpath, adding the full Clojure
  runtime as a hard dependency.
- A first-class Java API requires writing explicit Java adapters — the full public surface must be
  reimplemented in Java or via Clojure's `gen-class` directive with significant boilerplate.

**Verdict:** A Clojure-based implementation cannot provide a first-class Java API without
substantial additional wrapper code. The dynamic nature conflicts with slact's typed actor model.

### Dependency Cost

| Artifact                       | Required | Approximate size |
|--------------------------------|----------|------------------|
| `org.clojure:clojure`          | Always   | ~3.7 MB          |
| `org.clojure:core.async`       | Optional | ~200 KB          |

### Summary

| Criterion                 | Rating  | Notes                                                                           |
|---------------------------|---------|---------------------------------------------------------------------------------|
| Actor model fit           | ★★★☆☆   | `agent` primitive exists, but paradigm mismatch with typed actors               |
| Lean dependency footprint | ★★☆☆☆   | Full Clojure runtime (~3.7 MB) required                                          |
| Java API quality          | ★☆☆☆☆   | Needs complete Java wrapper layer; dynamic types conflict with typed API         |
| Gradual migration         | ★★☆☆☆   | Cannot mix Clojure and Java at source level naturally                           |
| Learning curve            | ★☆☆☆☆   | Entirely different paradigm (LISP, dynamic, functional-first)                    |

---

## Comparative Summary

| Criterion                      | Java 21 (current) | Kotlin        | Scala 3       | Clojure       |
|--------------------------------|-------------------|---------------|---------------|---------------|
| Actor model fit                | ★★★★☆             | ★★★★★         | ★★★★★         | ★★★☆☆         |
| Lean dependency footprint      | ★★★★★             | ★★★☆☆         | ★★☆☆☆         | ★★☆☆☆         |
| Java API quality               | ★★★★★             | ★★★★★         | ★★☆☆☆         | ★☆☆☆☆         |
| Gradual migration              | —                 | ★★★★★         | ★★★☆☆         | ★★☆☆☆         |
| Learning curve (for Java devs) | —                 | ★★★★☆         | ★★☆☆☆         | ★☆☆☆☆         |
| Tooling / ecosystem maturity   | ★★★★★             | ★★★★★         | ★★★★☆         | ★★★☆☆         |

---

## Recommendation

**For the current project goals, migration is not recommended at this time.**

Java 21 already provides the essential modern language features (records, sealed interfaces,
pattern matching, `var`) that motivated this evaluation. The codebase is clean, concise, and idiomatic.
Migrating solely to reduce boilerplate would provide marginal benefit at a real dependency cost.

**If a migration is undertaken in the future, Kotlin is the only sensible choice**, for the
following reasons:

1. **Incremental migration** — Kotlin and Java files can coexist in the same Gradle module.
   Migration can be done file by file without breaking the build or tests.

2. **Null safety in the type system** — eliminates all `@NotNull`/`@Nullable` annotations and
   removes the `org.jetbrains:annotations` dependency entirely (folded into `kotlin-stdlib`).

3. **No dedicated Java API wrapper needed** — the public Kotlin API is directly callable from Java
   for all non-suspend code. A rule of thumb: keep all public API methods non-suspend and use
   coroutines only in internal dispatch logic.

4. **Coroutines as optional upgrade path** — virtual threads (Java 21 JEP 444) could replace the
   current `ScheduledExecutorService` internally without changing the API; coroutines offer the
   same benefit for a Kotlin rewrite. The choice between the two can be made at the time of
   migration.

5. **Small, predictable footprint** — `kotlin-stdlib` (~1.8 MB) is the only mandatory addition;
   `kotlinx-coroutines-core` is optional and only needed if coroutines are adopted.

**Scala** is powerful but over-engineered for this project's scope and requires a separately
maintained Java API facade, which is at odds with the lean-dependency goal.

**Clojure** is fundamentally incompatible with slact's typed, statically-checked actor model and
would require a complete reimplementation of the Java API layer.

### Decision Criteria for Future Kotlin Migration

Before migrating, the following conditions should be met:

- [ ] Java 21 language features no longer cover the use case (e.g. complex coroutine-based
  scheduling is needed that virtual threads cannot replace).
- [ ] The `kotlin-stdlib` dependency is acceptable to downstream consumers.
- [ ] A migration plan that keeps the public Java API stable is in place.
- [ ] The team is comfortable with Kotlin idioms and coroutine semantics.
