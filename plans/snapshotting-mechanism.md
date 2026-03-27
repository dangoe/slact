# Concept: Snapshotting Mechanism

## Overview

Snapshotting allows a `SnapshotCapablePersistentActor` to avoid replaying the entire event log
on recovery. Instead of loading every event since the beginning of time, the actor loads the
latest snapshot (which encodes accumulated state up to a certain point in the event log) and
then replays only the events written *after* that snapshot. This is a pure performance
optimisation and does not change observable behaviour.

Snapshotting is strictly opt-in. Actors that do not need it extend `PersistentActor` and work
exclusively with `EventStore<E>`, which has no snapshot awareness at all.

---

## Persistence Model

Two database tables are involved:

### `events` table (existing, requires migration)

| Column               | Type                       | Notes                                      |
|----------------------|----------------------------|--------------------------------------------|
| `partition_key`      | `VARCHAR(255)`             | Actor stream identifier                    |
| `ordering`           | `BIGINT`                   | Position in the stream                     |
| `timestamp`          | `TIMESTAMP WITH TIME ZONE` |                                            |
| `payload`            | `BYTEA`                    | Serialised domain event **or** serialised `SnapshotMarkerPayload` |
| `is_snapshot_marker` | `BOOLEAN NOT NULL DEFAULT FALSE` | Distinguishes markers from domain events at SQL level |

Primary key: `(partition_key, ordering)`.

### `snapshots` table (new)

| Column                    | Type                    | Notes                                          |
|---------------------------|-------------------------|------------------------------------------------|
| `partition_key`           | `VARCHAR(255)`          | Actor stream identifier                        |
| `ordering`                | `BIGINT`                | Position of this snapshot entry in the stream  |
| `applied_up_to_ordering`  | `BIGINT`                | Ordering of the last event compacted           |
| `timestamp`               | `TIMESTAMP WITH TIME ZONE` |                                             |
| `payload`                 | `BYTEA`                 | Serialised snapshot state                      |

Primary key: `(partition_key, ordering)`.

---

## The Ordering Model

Both tables share the **same logical ordering sequence** for a given `partition_key`. This means
a snapshot entry at ordering `N` and a domain event at ordering `N` are mutually exclusive —
the database unique constraint on `(partition_key, ordering)` guarantees this.

The actor (and the store) always track the **last known maximum ordering** for the partition in
memory. Every write — whether a domain event or a snapshot — increments from `lastMaxOrdering + 1`.

This is the core of the **optimistic concurrency control** strategy:

> The Java code sets the expected next ordering. If another actor instance has already written
> at that ordering, the DB unique constraint fires, which the store translates into a
> `ConcurrentWriteException`.

---

## Snapshot Marker Event

Saving a snapshot involves writing to *two* different tables. Without coordination, actor A could
simultaneously append a domain event at ordering `N` while actor B writes a snapshot at ordering
`N` — and actor A would not know that the event log now contains a snapshot entry for that slot.

To close this race condition, **every snapshot write is accompanied by a snapshot marker
event** written to the `events` table at the same ordering as the snapshot. Both writes happen
in a **single atomic database transaction**: either both succeed or neither does.

```
events table
┌──────────────┬──────────┬───────────┬──────────────────────────────────────┬────────────────────┐
│ partition_key│ ordering │ timestamp │ payload                              │ is_snapshot_marker │
├──────────────┼──────────┼───────────┼──────────────────────────────────────┼────────────────────┤
│ actor-42     │       0  │  t0       │ <UserRegistered>                     │ false              │
│ actor-42     │       1  │  t1       │ <EmailVerified>                      │ false              │
│ actor-42     │       2  │  t2       │ <SnapshotMarkerPayload(ordering=2, appliedUpTo=1)> │ true               │  ← marker
│ actor-42     │       3  │  t3       │ <AddressChanged>                     │ false              │
└──────────────┴──────────┴───────────┴──────────────────────────────────────┴────────────────────┘

snapshots table
┌──────────────┬──────────┬─────────────────────┬───────────┬───────────────┐
│ partition_key│ ordering │ applied_up_to_ord.   │ timestamp │ payload       │
├──────────────┼──────────┼─────────────────────┼───────────┼───────────────┤
│ actor-42     │       2  │         1            │  t2       │ <Snapshot S>  │
└──────────────┴──────────┴─────────────────────┴───────────┴───────────────┘
```

In this example:
- Events 0 and 1 (`UserRegistered`, `EmailVerified`) were compacted into snapshot `S`.
- The snapshot entry has `ordering = 2` and `applied_up_to_ordering = 1`.
- A marker event occupies ordering 2 in the events table.
- Event 3 (`AddressChanged`) arrived after the snapshot.

The snapshot marker payload is a serialised internal value object — not a domain event `E`:

```java
record SnapshotMarkerPayload(long snapshotOrdering, long appliedUpToOrdering)
    implements Serializable {}
```

This makes the event log **self-describing**: from the marker alone a reader can identify
exactly which snapshot it refers to (`snapshotOrdering` → `snapshots` PK) and the range of
events the snapshot covers (everything up to and including `appliedUpToOrdering`), without
requiring a cross-table lookup.

The `is_snapshot_marker` column is still needed to filter markers efficiently at the SQL level
(avoiding payload deserialisation on every row). Together the two fields serve different
purposes:

| Field | Purpose |
|---|---|
| `is_snapshot_marker` | Fast SQL-level exclusion from `loadEvents` results |
| `payload` (`SnapshotMarkerPayload`) | Self-describing reference to the snapshot entry |

`loadEvents` always filters `AND is_snapshot_marker = FALSE`, so domain actors never encounter
marker rows regardless of the `fromOrdering` used.

---

## Saving a Snapshot

When a `SnapshotCapablePersistentActor` decides to save a snapshot:

1. It computes the current `lastMaxOrdering` (e.g. `1` in the example above).
2. It determines the next ordering: `snapshotOrdering = lastMaxOrdering + 1` (e.g. `2`).
3. In a single DB transaction (auto-commit disabled):
   - Inserts a snapshot marker into `events` at `(partition_key, ordering = 2,
     is_snapshot_marker = TRUE, payload = SnapshotMarkerPayload(snapshotOrdering=2, appliedUpToOrdering=1))`.
   - Inserts the snapshot into `snapshots` at `(partition_key, ordering = 2,
     applied_up_to_ordering = 1)`.
4. If any other writer has concurrently used ordering `2`, the unique constraint fires → the
   transaction rolls back → `ConcurrentWriteException` is raised.
5. On success, the actor updates its internal `lastMaxOrdering` to `2`.

---

## Recovery with a Snapshot

On startup a `SnapshotCapablePersistentActor` follows this recovery sequence:

1. **Load latest snapshot** — `loadLatestSnapshot(partitionKey)`.
2. If a snapshot is found:
   - Apply the snapshot state by calling `applySnapshot(snapshotEnvelope)`.
   - Load only the domain events written **after** the marker:
     `loadEvents(partitionKey, snapshot.ordering + 1)`.
     Because `loadEvents` always filters `is_snapshot_marker = FALSE`, only domain events are
     returned — no markers can leak into the replay sequence regardless of how many snapshots
     have been taken.
   - Replay those events in order.
3. If no snapshot is found:
   - Load the full event log: `loadEvents(partitionKey)`.
   - Replay all events.

This ensures the actor reaches the same final state regardless of whether a snapshot exists.
The snapshot is purely an acceleration of recovery — no domain logic depends on it.

---

## Concurrency Safety Summary

| Scenario                                        | Protected by                                  |
|-------------------------------------------------|-----------------------------------------------|
| Two actors append events simultaneously         | Unique constraint on `events(partition_key, ordering)` |
| Two actors save a snapshot simultaneously       | Unique constraint on `snapshots(partition_key, ordering)` |
| Actor A appends event while actor B saves snapshot | Snapshot marker in `events` occupies the same ordering slot; unique constraint fires for one of them |
| Snapshot rows and event rows collide at the same ordering | Shared ordering space + marker in `events` make this impossible |

In all cases the losing writer receives a `ConcurrentWriteException`, which signals that it must
reload its state before retrying.

---

## Interface Sketch

```java
// SnapshotEnvelope — two ordering fields
public record SnapshotEnvelope<S>(
    long ordering,              // position of this entry in the stream (= marker ordering)
    long appliedUpToOrdering,   // last domain event ordering compacted into this snapshot
    Instant timestamp,
    S snapshot
) {}

// SnapshotCapableEventStore — extends EventStore with snapshot operations
public interface SnapshotCapableEventStore<E, S> extends EventStore<E> {

    RichFuture<SnapshotEnvelope<S>> saveSnapshot(
        PartitionKey key, long lastMaxOrdering, S snapshot);

    RichFuture<Optional<SnapshotEnvelope<S>>> loadLatestSnapshot(PartitionKey key);
}
```

The `lastMaxOrdering` parameter on `saveSnapshot` mirrors the same parameter on
`appendMultiple` — it is the actor's last known maximum ordering and is used to compute
`snapshotOrdering = lastMaxOrdering + 1`, which becomes the ordering for both the marker event
and the snapshot row.

---

## Required Schema & Code Changes

### Schema migrations

| Migration | Change |
|-----------|--------|
| `0004_add_is_snapshot_marker_to_events.yaml` | Add `is_snapshot_marker BOOLEAN NOT NULL DEFAULT FALSE` column to `events` |
| `0003_create_snapshots_table.yaml` *(update)* | Add `applied_up_to_ordering BIGINT NOT NULL` column to `snapshots` |

### `SnapshotEnvelope` record

Add `appliedUpToOrdering` field (currently only `ordering` exists):

```java
public record SnapshotEnvelope<S>(
    long ordering,
    long appliedUpToOrdering,
    Instant timestamp,
    S snapshot
) {}
```

### `JdbcDialect.loadEvents` (and `PostgreSqlDialect` implementation)

Append `AND is_snapshot_marker = FALSE` to all `SELECT … FROM events` queries so marker rows
are filtered at the database level for both snapshot-capable and plain event stores.

### `SnapshotCapablePersistentActor.loadRecoveryData`

Change `loadEvents(partitionKey, latestSnapshotEnvelope.ordering())` to
`loadEvents(partitionKey, latestSnapshotEnvelope.ordering() + 1)` so recovery starts *after*
the marker rather than *at* it.

### `JdbcDialect` — new `insertSnapshot` method

```java
<S> SnapshotEnvelope<S> insertSnapshot(
    Connection connection,
    PartitionKey partitionKey,
    long lastMaxOrdering,
    S snapshot) throws SQLException, ConcurrentWriteException;
```

The implementation must:
1. Disable auto-commit on the connection.
2. Serialise a `SnapshotMarkerPayload(snapshotOrdering = lastMaxOrdering + 1,
   appliedUpToOrdering = lastMaxOrdering)` and insert it into `events`
   (`is_snapshot_marker = TRUE`).
3. Insert the snapshot row into `snapshots` (`ordering = lastMaxOrdering + 1`,
   `applied_up_to_ordering = lastMaxOrdering`).
4. Commit. On unique-constraint violation, roll back and throw `ConcurrentWriteException`.

