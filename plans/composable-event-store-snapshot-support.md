# Plan: Composable EventStore / SnapshotSupport Design

## Problem

The current design pollutes `EventStore<E, S>` and `PersistentActor<M, E, S>` with a snapshot type
parameter that is meaningless for non-snapshotting actors. Stores like `JdbcEventStore` and
`InMemoryEventStore` are forced to carry `SnapshotPayload.None` as a dead sentinel. When snapshot
methods are added to `EventStore`, they would be equally meaningless on non-snapshot stores.

## Proposed Approach

Follow the Scala "mixin" composition idiom in Java:
- **`EventStore<E>`** — pure event log with no snapshot awareness
- **`SnapshotSupport<E, S>`** — orthogonal interface with snapshot-specific methods
- **`SnapshotCapableEventStore<E, S>`** — combined interface (`EventStore<E>` + `SnapshotSupport<E, S>`)

Actors that need snapshotting extend `SnapshotCapablePersistentActor`; all others extend the
simpler `PersistentActor<M, E>`.

## New Type Hierarchy

```
Actor<M>
  └── AbstractPersistentActor<M, E, ST extends EventStore<E>>   [package-private; shared logic]
        ├── PersistentActor<M, E>                               [public; EventStore<E>]
        │     └── SimplePersistentActor<M, E>                   [alias, kept for compat]
        └── SnapshotCapablePersistentActor<M, E, S>             [public; SnapshotCapableEventStore<E,S>]

EventStore<E>                                   [loadEvents, appendMultiple]
  └── SnapshotCapableEventStore<E, S>           [extends EventStore + SnapshotSupport]
        └── SnapshotSupport<E, S>               [saveSnapshot, loadLatestSnapshot]

EventEnvelope<E>                               [plain record: ordering, timestamp, event]
SnapshotEnvelope<S>                            [new record: ordering, timestamp, snapshot]
SnapshotPayload                                [marker interface; None sentinel removed]
```

`AbstractPersistentActor` is **package-private** and never part of the public API. It holds all
shared machinery: the event store field, `persist`/`persistMultiple`, `events()`, `afterRecovery()`
hook, and the recovery message types. Subclasses implement two hooks:

```java
// How to resolve the concrete store type from the extension
protected abstract ST resolveStore(PersistenceExtension ext, PartitionKey key);

// How to initiate async recovery (pipe the right futures to self)
protected abstract void initiateRecovery(ST store, PartitionKey key);
```

`PersistentActor` pipes `loadEvents`; `SnapshotCapablePersistentActor` pipes
`loadLatestSnapshot` + `loadEvents(since snapshot)` and overrides `handleRecovery` to call
`applySnapshot` before replaying events.

## Changes

### persistence module

| File | Action | Notes |
|------|--------|-------|
| `EventEnvelope.java` | **Simplify** | Sealed interface → plain record `(long ordering, Instant timestamp, E event)` |
| `EventStore.java` | **Simplify** | Remove `S` type param; return `EventEnvelope<E>` |
| `SnapshotPayload.java` | **Simplify** | Remove inner `None` enum (no longer a sentinel) |
| `SnapshotEnvelope.java` | **New** | Record `(long ordering, Instant timestamp, S snapshot)` |
| `SnapshotSupport.java` | **New** | Interface with `saveSnapshot` and `loadLatestSnapshot` |
| `SnapshotCapableEventStore.java` | **New** | Extends `EventStore<E>` + `SnapshotSupport<E, S>` |
| `AbstractPersistentActor.java` | **New (package-private)** | Holds shared fields and logic: store field, `persist`/`persistMultiple`, `events()`, `afterRecovery()`, recovery message types; parameterized on `ST extends EventStore<E>` |
| `PersistentActor.java` | **Simplify** | Remove `S` type param; extends `AbstractPersistentActor<M, E, EventStore<E>>`; resolves via `PersistenceExtension.resolveStore` |
| `SnapshotCapablePersistentActor.java` | **New** | Extends `AbstractPersistentActor<M, E, SnapshotCapableEventStore<E,S>>`; adds `persistSnapshot(S)`, abstract `applySnapshot(SnapshotEnvelope<S>)`; recovery applies snapshot then replays events |
| `SimplePersistentActor.java` | **Simplify** | Just `extends PersistentActor<M, E>` (no type params to bind) |
| `PersistenceExtension.java` | **Update** | `resolveStore` returns `Optional<EventStore<E>>`; add `resolveSnapshotCapableStore` returning `Optional<SnapshotCapableEventStore<E, S>>` |

### persistence module (test)

| File | Action |
|------|--------|
| `InMemoryEventStore.java` | **Update** — implements `EventStore<E>` (remove `SnapshotPayload.None`) |
| `PersistentActorTest.java` | **Update** — adjust `PersistenceExtension` anonymous impl |

### persistence-jdbc module

| File | Action |
|------|--------|
| `JdbcDialect.java` | **Update** — return `EventEnvelope<E>` (no `SnapshotPayload.None`) |
| `JdbcEventStore.java` | **Update** — implements `EventStore<E>` (remove `SnapshotPayload.None`) |
| `JdbcPersistentActorTest.java` | **Update** — adjust test setup if needed |

## Interface Sketches

### `SnapshotSupport<E, S>`
```java
public interface SnapshotSupport<E, S extends SnapshotPayload> {
    @NotNull RichFuture<SnapshotEnvelope<S>> saveSnapshot(
        @NotNull PartitionKey key, @NotNull S snapshot);

    @NotNull RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(
        @NotNull PartitionKey key);
}
```

### `SnapshotCapableEventStore<E, S>`
```java
public interface SnapshotCapableEventStore<E, S extends SnapshotPayload>
    extends EventStore<E>, SnapshotSupport<E, S> {
}
```

### `SnapshotCapablePersistentActor<M, E, S>`
```java
public abstract class SnapshotCapablePersistentActor<M, E, S extends SnapshotPayload>
    extends AbstractPersistentActor<M, E, SnapshotCapableEventStore<E, S>> {

    // Recovery: load latest snapshot → apply it → replay events after snapshot ordering
    protected abstract void applySnapshot(@NotNull SnapshotEnvelope<S> snapshot);
    protected final void persistSnapshot(@NotNull S snapshot) { ... }
    // persist/persistMultiple/events()/afterRecovery() inherited from AbstractPersistentActor
}
```

### Updated `PersistenceExtension`
```java
public interface PersistenceExtension {
    <E> @NotNull Optional<EventStore<E>> resolveStore(@NotNull PartitionKey key);

    <E, S extends SnapshotPayload> @NotNull Optional<SnapshotCapableEventStore<E, S>>
        resolveSnapshotCapableStore(@NotNull PartitionKey key);
}
```

## Notes & Decisions

- `SnapshotPayload` is kept as a pure marker interface (the `None` sentinel is no longer needed).
- `EventEnvelope` becomes a plain record (no sealed hierarchy since snapshot data is now in
  `SnapshotEnvelope`).
- `SnapshotCapablePersistentActor` extends `AbstractPersistentActor` (not `Actor<M>` directly),
  eliminating all code duplication while keeping the distinct snapshot-aware recovery flow.
- `SimplePersistentActor` is kept for backwards compatibility but is now trivially just
  `extends PersistentActor<M, E>`.
- `loadEvents` on `EventStore<E>` continues to load the full event log; snapshot-optimised
  recovery (skip events before latest snapshot) is handled in `SnapshotCapablePersistentActor`,
  which can use `loadLatestSnapshot` + a future `loadEventsSince` overload when needed.
