# Technical Review: slact Actor Library

> **Scope:** Full review of the `core`, `testkit`, `persistence`, and `persistence-jdbc` modules
> against the goal of a _small-footprint, performant Java actor library_.
> The persistent-actor section is treated as **work-in-progress** and its findings are framed as
> planning input rather than immediate action items.

---

## 1. Executive Summary

The library has a clean foundation: a typed, single-threaded-per-actor mailbox model with
lock-free CAS-based scheduling, hierarchical supervision, and a well-separated persistence
extension. Several correctness bugs and avoidable performance costs were identified during
this review. The user-facing API has a few rough edges that would trip up new users. None of
the findings require architectural changes; all can be addressed incrementally.

---

## 2. Bugs (already fixed in this PR)

| # | Location | Severity | Finding |
|---|----------|----------|---------|
| B-1 | `DefaultSlactContainer.shutdown()` | **Critical** | Timeout condition `start.isAfter(start.plusSeconds(60))` always evaluates to `false` — same `Instant` on both sides. Container busy-polls indefinitely on a hung stop. **Fixed:** `eventualResult.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)`. |
| B-2 | `ActorWrapper.processMessages()` | **High** | `break` on unresolvable sender drops the rest of the in-flight batch, deferring it to the next scheduling cycle. A single dead reference degraded throughput for the entire batch. **Fixed:** `continue` to skip only the offending item. |
| B-3 | `ActorWrapper.processMessages()` | **High** | Early `return` when the actor is stopped did not call `mailboxProcessingActive.set(false)`, permanently locking the flag. **Fixed:** explicit reset before `return`. |
| B-4 | `ActorWrapper.StoppingActorLogic.completeStop()` | **High** | Pending `requestResponseTo` futures in both the mailbox queue and `messagesWithResponseRequest` were abandoned on stop, blocking callers indefinitely. **Fixed:** drain with `poll()` loop and cancel all non-stop futures. |

---

## 3. Performance Issues

### P-1 · `UUID.randomUUID()` per `MailboxItem` (fixed)

Every `MailboxItem` construction called `UUID.randomUUID()`, which uses a
`SecureRandom` internally — contended and slow under high message rates. Replaced with a
static `AtomicLong` sequence counter (`getAndIncrement()`), which is a single CAS on a
cache-hot cache line.

**Impact:** Significant improvement at high message rates (millions of messages/sec).
The `id` type changed from `String` to `long`, which also cuts per-item allocation by
roughly 64 bytes (UUID string object + char array).

> **Follow-up:** The sequential `long` ID wraps around after ~9 × 10¹⁸ messages. For a
> library this is practically infinite, but if the ID is ever used for deduplication
> across container restarts a monotonic-clock timestamp or a composite ID would be needed.

### P-2 · Millisecond precision in `ScheduledExecutor` (fixed)

`scheduleOnce(Duration.ZERO, …)` was internally converted to `toMillis() = 0 ms`,
effectively scheduling with zero-delay. While functionally correct, the conversion lost
sub-millisecond precision for any non-zero delays. Changed to nanosecond precision
(`toNanos()` / `NANOSECONDS`).

**Impact:** More accurate periodic scheduling (e.g., exactly 1 ms tick) and better
behaviour of `Duration.ofNanos(…)` passed from user code.

### P-3 · New `ActorContextImpl` allocation per message

`processMessages()` allocates a fresh `ActorContextImpl` for every mailbox item, even
though the only varying field is `senderHandle`. While modern JVMs can often escape-analyse
and stack-allocate short-lived objects, a reusable context that exposes a
`resetSender(handle)` method would eliminate the GC pressure entirely on hot paths.

**Recommendation (future):** Introduce a mutable `ActorContextImpl` held on the actor
wrapper and reset per message, guarded by the existing single-threaded processing guarantee.

### P-4 · `ScheduledExecutorService.schedule()` call per message append

Every call to `appendMessage()` (i.e., every message sent to an actor) calls
`rescheduleMessageProcessing()`, which submits a new task to the underlying
`ScheduledExecutorService`. This means a system with N actors receiving messages
simultaneously produces N scheduler submissions, each requiring a queue insertion and
heap rebalancing in `DelayQueue`.

**Recommendation (future):** Use a single-CAS "notify" model: if
`mailboxProcessingActive` is already `false`, set it to `true` and submit exactly one task.
Skip the `reschedule` call if the processing loop is already running (it already reschedules
itself at the end of each batch). The current approach is correct but redundant — the
rescheduling path at the end of `processMessages()` is sufficient.

### P-5 · Shared `ScheduledExecutorService` for both scheduling and actor dispatch

The single `ScheduledExecutor` is used both for actor mailbox processing and for
`pipeFuture` / `scheduleOnce` / `scheduleAtFixedRate` tasks called from user code. A
long-running `onMessage` handler (or a slow `pipeFuture` target) can starve the scheduler
thread pool, delaying all other actors.

**Recommendation (future):** Separate the dispatcher thread pool (pure `ThreadPoolExecutor`)
from the scheduler (`ScheduledExecutorService`). The scheduler only needs to be responsible
for periodic triggers; actual message processing runs on the dispatcher pool.

### P-6 · `ConcurrentHashMap` for `children` — unnecessary in single-threaded context

`ActorWrapper.children` is typed as `ConcurrentHashMap`, but it is only ever mutated
from within the actor's own mailbox processing thread (via `registerChild` and
`unregisterChild`). Reads happen from child actors sending `ActorStoppedEvent`, which
triggers `unregisterChild` within the parent's processing loop. A plain `HashMap` with
visibility guaranteed by the existing `AtomicReference<ActorLogic>` write barrier would be
sufficient and cheaper.

**Recommendation (future):** Replace `ConcurrentHashMap` with `HashMap` for `children`.
The `ActorLogic` swap acts as a happens-before barrier between the spawning thread and
the processing thread.

### P-7 · `InMemoryEventStore`: per-append full-stream scan for `lastOrdering`

`appendMultiple` streams the entire partition list to find `max(ordering)` on every call:

```java
final var lastOrdering = this.events.getOrDefault(storeKey, …)
    .stream().mapToLong(EventEnvelope::ordering).max().orElse(-1L);
```

For long-lived actors this is O(N) per write. It should track the per-partition maximum
in a separate `ConcurrentHashMap<StoreKey, AtomicLong>`.

**Recommendation (future):** Replace the scan with a per-partition `AtomicLong` counter.

---

## 4. Design Concerns

### D-1 · `ActiveActorContextHolder` is a global singleton

`ActiveActorContextHolder.getInstance()` returns a JVM-wide singleton. This means:

- Multiple `SlactContainer` instances in the same JVM share the same `ThreadLocal`,
  making it impossible to test two containers on the same thread without interference.
- If user code calls `context()` from a thread-pool thread (e.g., inside a `CompletableFuture`
  callback) it silently accesses whatever context was last activated on that OS thread,
  which may be wrong or `null`.

**Recommendation (future):** Scope `ActiveActorContextHolder` to the `SlactContainer`
instance and pass it explicitly to `ActorWrapper` (already done) and to the `Actor` base
class (not done — `Actor` currently calls `ActiveActorContextHolder.getInstance()` directly).

### D-2 · `Actor.context()` hides lifecycle violations

Because `context()` is accessed via the global `ActiveActorContextHolder`, calls to
`context()` outside of a message handler (e.g., in the actor constructor or from a
background thread) throw `IllegalStateException` at runtime rather than failing at compile
time. There is no compile-time enforcement that `context()` is only called from `onMessage`,
`onStart`, or `onStop`.

**Recommendation (future):** Document this contract explicitly and consider adding a
`@MustBeCalledFromMessageHandler` annotation or equivalent IDE warning. Alternatively,
expose context operations through method parameters (the "receive" pattern), though that
would be a breaking API change.

### D-3 · `StopActorCommand` embeddable as a user message

`ReadyActorLogic.processMailboxItem()` checks `if (message instanceof StopActorCommand)`
**after** casting the `WrappedMessage` payload to `M`. This means any actor with
`M = Object` (e.g., the root actor) could accidentally handle a `StopActorCommand` as a
user message if it is sent via `send()`. The lifecycle command leaks into the public message
type space.

**Recommendation (future):** Keep lifecycle messages entirely separate from user messages;
do not allow them to be wrapped in `WrappedMessage`. The check inside `ReadyActorLogic` for
`StopActorCommand` as a user message type is a design smell.

### D-4 · `requestResponseTo` uses sender path as correlation key

The response-correlation map `messagesWithResponseRequest` uses `sender.path()` as the
key. This means:

- Only one outstanding request per sender is supported at a time. A second request from
  the same sender before the first has responded will silently overwrite the first future.
- The pattern is brittle: if the sender path is reused (actor restarted with the same name)
  the wrong future may be completed.

**Recommendation (future):** Use a per-request `UUID` (or the `MailboxItem.id`) as the
correlation key instead of the sender path.

### D-5 · `pipeFuture` timeout is hardcoded to 10 seconds with no propagation of cancellation

```java
final var message = eventualMessage.get(10, TimeUnit.SECONDS);
```

The 10-second timeout is a hardcoded constant (marked `TODO Configure timeout`). If the
future times out, a `RuntimeException` wrapping a `TimeoutException` is thrown, which is
caught by the global exception handler in `processMessages()` and only logged — the actor
continues silently without receiving the piped result. The caller has no way to know the
pipe failed.

**Recommendation (future):** 
1. Add configurable timeout via `SlactContainerBuilder`.
2. On timeout, send a dedicated failure message to the target actor (or use a
   `CompletionStage`-aware approach that reacts to cancellation).

### D-6 · `RoutingActor` spawns workers in `onStart` without guarding for readiness

`RoutingActor.roundRobinWorker` spawns child workers inside the `RoutingStrategy` factory
which is called from `onStart`. This is correct, but the routing actor may receive messages
before its children have completed their own `onStart`. Messages forwarded to children that
are still in `StartupActorLogic` will be queued correctly, so there is no bug — but the
pattern is not obvious and could confuse users who assume the strategy is "hot" immediately.

**Recommendation (future):** Document this explicitly, or provide a `awaitReady` guard
inside `roundRobinWorker`.

### D-7 · `ActorCreator` is a plain `@FunctionalInterface` — no lifecycle enforcement

`ActorCreator.create()` is called during `spawnInternal`. Any checked exception thrown by
`create()` propagates as an unhandled exception from the spawning call site and could leave
a partially-registered actor in the registry.

**Recommendation (future):** Wrap `create()` in a try-catch inside `spawnInternal`; if
creation fails, ensure the actor is never registered and throw a descriptive
`ActorRegistrationException`.

### D-8 · `DefaultSlactContainer.shutdown()` is not idempotent

Calling `shutdown()` a second time after the container is already stopped calls `stop(rootActor)`
again. The root actor is already stopped and its handle is unresolvable, so the `stop` future
will never complete, causing the second shutdown call to block for
`SHUTDOWN_TIMEOUT_SECONDS` (60 s) before timing out.

**Recommendation (future):** Guard the shutdown method with the existing `stopped` flag:
```java
if (!this.stopped.compareAndExchange(false, true)) {
    return; // already shut down
}
```

### D-9 · `PersistenceExtensionHolder` is a JVM-wide singleton

Like `ActiveActorContextHolder`, `PersistenceExtensionHolder` is a static singleton.
This makes it impossible to run two actor systems with different persistence configurations
in the same JVM and prevents safe parallel test execution without explicit `clear()` calls.

**Recommendation (future):** Inject `PersistenceExtension` directly into the container
(via `SlactContainerBuilder.withPersistenceExtension(…)`) and propagate it to persistent
actors through the actor context or a scoped holder.

---

## 5. User API Issues

### A-1 · `ActorHandle.send()` requires an active context

`ActorHandle.send(M)` throws `IllegalStateException` if called outside an active actor
context. This means external callers (tests, HTTP handlers) **must** use
`SlactContainer.send(M).to(actor)` instead — but this asymmetry is not clear from the
interface name and signature alone. Users would expect `handle.send(msg)` to always work.

**Recommendation:** Either remove `ActorHandle.send(M)` and always route through the
container, or add a variant `ActorHandle.sendFromOutside(M)` that uses the root actor as
a synthetic sender. Document the constraint prominently.

### A-2 · Fluent send API is verbose for the common case

The send fluent API requires chaining `.to(target)` even for the simplest case:

```java
send("hello").to(otherActor);                   // 2 calls
requestResponseTo("query").ofType(String.class).from(target); // 3 calls
```

The `ofType(Class<R>)` step on `requestResponseTo` does not add type safety at runtime
(the cast is unchecked) and only serves as documentation. It adds boilerplate with no
compile-time benefit.

**Recommendation:** Consider collapsing `requestResponseTo(M).ofType(R.class).from(target)`
to `requestResponseTo(M, target)` returning `Future<Object>` (or a typed variant with
a single call). Alternatively use a builder pattern with an optional `.ofType()` hint.

### A-3 · `ActorSpawner.spawn(ActorCreator)` uses a random UUID name by default

Spawning an actor without specifying a name generates a random UUID string as the actor name:

```java
default ActorHandle<M> spawn(ActorCreator<A, M> creator) {
    return spawn(UUID.randomUUID().toString(), creator);
}
```

This makes actor paths completely opaque in logs and diagnostics
(`/6f3a1b2c-4d5e-...`). It also uses `UUID.randomUUID()` per spawn, which is expensive
for the same reasons as in `MailboxItem` (see P-1).

**Recommendation:** Replace with a short, monotonically increasing suffix
(`actor-1`, `actor-2`, …) or derive a name from the actor class name plus a counter.

### A-4 · No supervision strategy

When `onMessage` throws an exception, the actor continues processing the next message
silently. There is no mechanism for the parent to be notified of child failures, and there
is no restart or escalation policy.

For a library targeting reactive system semantics this is a significant gap. Akka's "Let it
crash" philosophy and Erlang's OTP supervision trees exist precisely to make fault isolation
explicit.

**Recommendation (future):** Introduce a `SupervisionStrategy` on `Actor` or
`ActorSpawner` with at minimum `RESUME`, `RESTART`, and `ESCALATE` options. A default
of `RESUME` (current behaviour) preserves backward compatibility.

### A-5 · No way to check if an actor has been stopped from inside the actor

An actor cannot query its own stopped state from within a message handler. If external code
stops the actor concurrently, the actor's in-flight `onMessage` completes normally, and
subsequent messages are silently dropped. There is also no `onStop` guarantee that the actor
won't receive new messages after `onStop` returns.

**Recommendation (future):** Expose `isStopped()` on `ActorContext` and document
the ordering guarantee between the last processed message and `onStop`.

### A-6 · `ActorContext.respondWith(Object)` loses type safety

```java
<M1> void respondWith(@NotNull M1 message);
```

The unconstrained `M1` means `respondWith(42)` compiles fine even when the response
future expects `String`, surfacing the type error only at runtime on the `.get()` call.

**Recommendation:** Constrain the method signature to the known response type when a
`MessageWithResponseRequest` is in scope. This may require a typed `RequestContext<M, R>`
that is distinct from the plain `ActorContext<M>`.

### A-7 · `SlactContainer` and `ActorRuntime` are a wide, flat interface

`SlactContainer` extends `ActorRuntime` which extends both `ActorHandleResolver` and
`ActorSpawner`. The result is a single mega-interface with ~10 methods covering spawn,
resolve, send, forward, requestResponse, pipeFuture, stop, shutdown, and isStopped.

User code that needs only message-sending or only path resolution must accept the whole
interface. Testing is harder because mock creation requires stubbing many unrelated methods.

**Recommendation (future):** Segregate `ActorRuntime` into smaller capability interfaces
(`MessageSender`, `ActorResolver`, `ActorSpawner`) and compose them in `SlactContainer`.
This follows the Interface Segregation Principle and makes the API contract at each callsite
explicit.

---

## 6. Persistence Module (WIP — planning input only)

The persistence layer is explicitly work-in-progress. The following observations are
intended as planning guidance.

### PX-1 · `persistMultiple` blocks the actor thread

```java
final var addedEvents = eventStore().appendMultiple(partitionKey, maxOrdering(), events)
    .join();  // ← blocks the mailbox processing thread
```

`RichFuture.join()` blocks the actor's dispatcher thread until the database responds.
Under load, all dispatcher threads can be blocked simultaneously, halting the entire
actor system.

**Planned fix:** Make persistence non-blocking by using `pipeFuture` to receive the
write acknowledgement as a message. The actor would temporarily switch to a stashing
behaviour while the write is in flight, resuming normal processing on success or
switching to an error behaviour on failure.

### PX-2 · `SnapshotCapablePersistentActor.persistMultiple` saves snapshot synchronously

Snapshot saving (`eventStore().saveSnapshot(…).join()`) also blocks the mailbox thread
for the same reasons as PX-1.

### PX-3 · `PersistentActorBase.onStart()` is `final` — extensibility blocked

The `onStart()` hook is sealed in `PersistentActorBase`, which means subclasses cannot
add custom startup logic (e.g., registering timers, spawning children) without overriding
`afterRecovery()`. This is documented but the naming is non-obvious. The semantics of
`afterRecovery()` and `onStart()` should be aligned.

### PX-4 · Concurrent-write retry is the actor's responsibility with no built-in support

When `ConcurrentWriteException` is thrown from `persist()`, the actor must catch it and
implement its own retry-reload logic. There is no framework support for this common
pattern, making it easy to implement incorrectly.

**Recommendation (future):** Provide a default retry-with-reload mechanism, or at minimum
a utility method on `PersistentActorBase` that reloads and re-applies events.

### PX-5 · `PartitionKey` equality relies on `getClass()` in `InMemoryEventStore`

```java
private record StoreKey(@NotNull Class<?> partitionKeyType, @NotNull String value) {}
```

The `StoreKey` uses `partitionKey.getClass()` (the concrete class of the partition key
implementation) as the stream-type discriminator. This means two different anonymous or
lambda-generated partition key implementations sharing the same `value()` string would be
treated as different streams — silent data isolation that could confuse users.

**Recommendation:** Document this discriminator behaviour in `PartitionKey`'s Javadoc and
in the `InMemoryEventStore` implementation.

### PX-6 · Snapshot marker events planned for removal

The snapshot marker event written into the `events` table (see `SnapshotMarkerPayload`) is
acknowledged as a transitional mechanism. Once the persistence testkit is introduced and
the snapshot-capable event store reaches stability, the marker should be removed in favour
of a pure `snapshots`-table lookup.

---

## 7. Prioritised Backlog

Items are grouped by effort and impact.

### Quick wins (low effort, high impact)

| ID | Task |
|----|------|
| B-1 … B-4 | ✅ **Fixed in this PR** |
| P-1, P-2 | ✅ **Fixed in this PR** |
| A-3 | Replace random UUID default actor name with a short counter-based name |
| D-8 | Make `DefaultSlactContainer.shutdown()` idempotent |
| P-7 | Replace full-scan `max(ordering)` in `InMemoryEventStore` with a per-partition counter |

### Medium effort

| ID | Task |
|----|------|
| P-3 | Reuse `ActorContextImpl` per actor (mutable, single-threaded) |
| P-4 | Remove redundant `scheduleOnce` calls on `appendMessage` |
| P-5 | Separate dispatcher pool from scheduler |
| D-4 | Use per-request ID as response-correlation key |
| D-5 | Make `pipeFuture` timeout configurable; propagate pipe failure as a message |
| A-1 | Fix `ActorHandle.send(M)` — remove context requirement or document it clearly |
| A-2 | Simplify `requestResponseTo` API |
| PX-1, PX-2 | Make persistence writes non-blocking (async with stashing) |

### Larger changes

| ID | Task |
|----|------|
| D-1 | Scope `ActiveActorContextHolder` to the container instance |
| D-9 | Inject `PersistenceExtension` through `SlactContainerBuilder` |
| A-4 | Introduce `SupervisionStrategy` |
| A-6 | Typed `RequestContext<M, R>` for request-response handlers |
| A-7 | Interface segregation for `ActorRuntime` / `SlactContainer` |
| PX-4 | Built-in concurrent-write retry support |

---

## 8. Reference: Current Architecture (concise)

```
SlactContainer (DefaultSlactContainer)
│  ScheduledExecutor  (shared thread pool, default 16 threads)
│  ActorRegistry      (ConcurrentHashMap<ActorPath, ActorWrapper>)
│  RootActorSpawner
│
└── ActorWrapper<M>  (per actor)
    │  mailboxItems          LinkedBlockingQueue<MailboxItem>
    │  mailboxProcessingActive  AtomicBoolean (CAS gate)
    │  actorLogic            AtomicReference<ActorLogic>  [Startup → Ready → Stopping]
    │  children              ConcurrentHashMap<ActorPath, ActorWrapper>
    │  messagesWithResponseRequest  HashMap<ActorPath, MessageWithResponseRequest>
    │
    ├── StartupActorLogic    Awaits CompleteStartActorCommand → calls onStart() → Ready
    ├── ReadyActorLogic      Processes user messages + stop/child-stopped lifecycle
    └── StoppingActorLogic   Propagates stop to children → calls onStop() → notifies parent
```

**Message path (send):**
1. Caller → `ActorWrapper.sendInternal()` → enqueue `FireAndForgetMessage`
2. `rescheduleMessageProcessing()` → `ScheduledExecutor.scheduleOnce(processMessages, ZERO)`
3. Thread-pool thread: `processMessages()` — CAS gate, batch loop (≤64), deactivate context
4. Actor's `onMessage()` executes on thread-pool thread

**Key invariants:**
- Single-threaded per actor (CAS gate in `processMessages`)
- Actor state is safe to mutate without locks inside message handlers
- Hierarchical shutdown: parent waits for all children before calling its own `onStop`
