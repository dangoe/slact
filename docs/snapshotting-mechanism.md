# Snapshotting Mechanism — Implementation Specification

This document specifies the data model, storage contract, and required operations for the
optional snapshotting extension. It is intentionally database-agnostic so that it can guide
the implementation of persistence dialects for any relational or document store.

Read [event-log.md](event-log.md) first; this document builds on the concepts defined there.

---

## Purpose

Snapshotting allows a `SnapshotCapablePersistentActor` to skip replaying the entire event log
on recovery. Instead of loading every event from the beginning of time, the actor loads the
most recent snapshot (which encodes accumulated state up to a certain point) and replays only
the events written *after* it. This is a pure performance optimisation — observable behaviour
is identical with or without snapshots.

Snapshotting is strictly opt-in. Actors that do not need it extend `PersistentActor` and use
`EventStore<E>` exclusively.

---

## Domain Model

### `SnapshotEnvelope<S>`

A read-only container returned by the store after a successful snapshot load.

| Field                 | Type      | Description                                                             |
|-----------------------|-----------|-------------------------------------------------------------------------|
| `ordering`            | `long`    | Position of this snapshot entry in the event stream                     |
| `appliedUpToOrdering` | `long`    | Ordering of the last domain event compacted into this snapshot          |
| `timestamp`           | `Instant` | Wall-clock time at which the snapshot was persisted                     |
| `snapshot`            | `S`       | The snapshot state payload                                              |

`ordering` is drawn from the **same partition-local sequence** used by domain events (see
[event-log.md § Ordering](event-log.md#ordering)). A snapshot entry at ordering `N` and a
domain event at ordering `N` are mutually exclusive within the same partition.

### `SnapshotMarkerPayload`

An internal, non-domain value object that is serialised as the `payload` of a marker row in
the `events` table. It is not exposed outside the persistence layer.

| Field                 | Type   | Description                                                         |
|-----------------------|--------|---------------------------------------------------------------------|
| `snapshotOrdering`    | `long` | The ordering of the associated snapshot entry (= its own ordering)  |
| `appliedUpToOrdering` | `long` | The ordering of the last domain event compacted into the snapshot   |

The marker payload makes the event log **self-describing**: from a marker row alone a reader
can identify the referenced snapshot entry (`snapshotOrdering` → `snapshots` PK) and the range
of events it covers (everything up to and including `appliedUpToOrdering`).

---

## Schema

### `events` Table (extended)

See [event-log.md § Schema](event-log.md#schema). The `is_snapshot_marker` column introduced
there is the only addition required by the snapshotting mechanism.

### `snapshots` Table (new)

| Column          | Type                       | Constraints                      |
|-----------------|----------------------------|----------------------------------|
| `partition_key` | `VARCHAR(255)`             | NOT NULL                         |
| `ordering`      | `BIGINT`                   | NOT NULL, part of PRIMARY KEY    |
| `event_ordering`| `BIGINT`                   | NOT NULL (`appliedUpToOrdering`) |
| `timestamp`     | `TIMESTAMP WITH TIME ZONE` | NOT NULL                         |
| `payload`       | binary / blob              | NOT NULL                         |

**Primary key:** `(partition_key, ordering)`.

**Index:** `partition_key` to support efficient partition-scoped lookups.

> The column is named `event_ordering` in the physical schema to avoid case-sensitivity issues
> with databases that distinguish quoted from unquoted identifiers (e.g. PostgreSQL). It maps
> to `SnapshotEnvelope.appliedUpToOrdering` in Java.

---

## The Snapshot Marker Event

Saving a snapshot writes to *two* different tables. Without coordination, actor A could
simultaneously append a domain event at ordering `N` while actor B writes a snapshot at
ordering `N` — and the two writes would not conflict at the database level.

To close this race, **every snapshot write is accompanied by a snapshot marker event** written
to the `events` table at the same ordering as the snapshot. Both writes happen in a **single
atomic database transaction**.

```
events table
┌──────────────┬──────────┬───────────────────────────────────────────────────┬────────────────────┐
│ partition_key│ ordering │ payload                                           │ is_snapshot_marker │
├──────────────┼──────────┼───────────────────────────────────────────────────┼────────────────────┤
│ actor-42     │       1  │ <UserRegistered>                                  │ false              │
│ actor-42     │       2  │ <EmailVerified>                                   │ false              │
│ actor-42     │       3  │ <SnapshotMarkerPayload(snapshotOrdering=3,        │ true               │ ← marker
│              │          │                        appliedUpToOrdering=2)>    │                    │
│ actor-42     │       4  │ <AddressChanged>                                  │ false              │
└──────────────┴──────────┴───────────────────────────────────────────────────┴────────────────────┘

snapshots table
┌──────────────┬──────────┬───────────────┬───────────────────────────┐
│ partition_key│ ordering │ event_ordering│ payload                   │
├──────────────┼──────────┼───────────────┼───────────────────────────┤
│ actor-42     │       3  │      2        │ <Snapshot S>              │
└──────────────┴──────────┴───────────────┴───────────────────────────┘
```

In this example:
- Events 1 (`UserRegistered`) and 2 (`EmailVerified`) were compacted into snapshot `S`.
- The snapshot entry has `ordering = 3` and `event_ordering = 2`.
- A marker event occupies ordering 3 in the `events` table.
- Event 4 (`AddressChanged`) arrived after the snapshot.

The `is_snapshot_marker` column and the marker `payload` serve distinct purposes:

| Mechanism              | Purpose                                                              |
|------------------------|----------------------------------------------------------------------|
| `is_snapshot_marker`   | Fast SQL-level exclusion in `loadEvents` without payload inspection  |
| `SnapshotMarkerPayload`| Self-describing cross-reference to the snapshot entry and its range  |

---

## Required Operations

### `loadLatestSnapshot(partitionKey) → Optional<SnapshotEnvelope<S>>`

Return the snapshot entry with the highest `ordering` for the given `partitionKey`, or an
empty result if none exists.

The snapshot with the highest ordering is always the most recent because orderings are
strictly monotonically increasing.

### `saveSnapshot(partitionKey, lastMaxOrdering, snapshot) → SnapshotEnvelope<S>` *(planned)*

Persist a snapshot atomically together with its event-log marker.

**Steps (within a single database transaction):**

1. Compute `snapshotOrdering = lastMaxOrdering + 1`.
2. Insert a marker row into `events`:
   - `ordering = snapshotOrdering`
   - `is_snapshot_marker = true`
   - `payload = serialise(SnapshotMarkerPayload(snapshotOrdering, appliedUpToOrdering = lastMaxOrdering))`
3. Insert the snapshot row into `snapshots`:
   - `ordering = snapshotOrdering`
   - `event_ordering = lastMaxOrdering`
   - `payload = serialise(snapshot)`
4. Commit. On unique-constraint violation on either table, roll back and throw
   `ConcurrentWriteException`.
5. Return a `SnapshotEnvelope` reflecting the persisted values.

The caller (actor) must then update its internal `lastMaxOrdering` to `snapshotOrdering`.

---

## Recovery Flow

On startup a `SnapshotCapablePersistentActor` follows this sequence:

```
loadLatestSnapshot(partitionKey)
│
├── Some(snapshotEnvelope)
│   ├── applySnapshot(snapshotEnvelope.snapshot)
│   └── loadEvents(partitionKey, snapshotEnvelope.ordering + 1)
│       └── replay each event in order
│
└── empty
    └── loadEvents(partitionKey)           ← full replay from the beginning
        └── replay each event in order
```

The `fromOrdering` passed to `loadEvents` is `snapshot.ordering + 1`, **not**
`snapshot.ordering`. This is critical: `snapshot.ordering` is the slot occupied by the marker
event. Because `loadEvents` always filters `is_snapshot_marker = FALSE` at the storage level,
the marker would never be returned anyway — but using `ordering + 1` is still the correct
semantic: the actor wants events strictly *after* the snapshot position.

---

## Concurrency Safety

All concurrency protection is derived from the **shared global ordering sequence** and the
unique-key constraints on both tables.

| Scenario                                         | Protected by                                             |
|--------------------------------------------------|----------------------------------------------------------|
| Two actors append domain events simultaneously   | Unique constraint on `events.(partition_key, ordering)`  |
| Two actors save a snapshot simultaneously        | Unique constraint on `snapshots.(partition_key, ordering)` |
| Actor A appends an event while actor B saves a snapshot at the same ordering | Marker inserted into `events` by B conflicts with A's event; unique constraint fires for one of them |
| Event and snapshot rows collide at the same ordering | Shared partition-local ordering space + marker in `events` make this impossible |

In every case the losing writer receives a `ConcurrentWriteException` and must reload state
before retrying.

---

## Serialisation

The `payload` column in `snapshots` follows the same serialisation contract as in `events` (see
[event-log.md § Serialisation](event-log.md#serialisation)). The dialect is solely responsible
for the format; the store interface is agnostic.

The `SnapshotMarkerPayload` serialised into the `events.payload` column is an internal
implementation detail of the dialect. A new dialect may use a different format for it as long
as it can round-trip the two ordering values.

---

## JDBC Dialect Contract

When implementing a new JDBC dialect the following method signatures must be fulfilled in
addition to those required by the plain event-log dialect:

```java
// Load latest snapshot
<S> Optional<SnapshotEnvelope<S>> loadLatestSnapshot(
    Connection connection, PartitionKey partitionKey) throws SQLException;

// Insert snapshot + marker atomically (auto-commit must be disabled by the time this is called)
<S> SnapshotEnvelope<S> insertSnapshot(
    Connection connection, PartitionKey partitionKey,
    long lastMaxOrdering, S snapshot) throws SQLException, ConcurrentWriteException;
```

**Referencing the PostgreSQL dialect:** `PostgreSqlDialect` is the canonical reference
implementation. Key SQL patterns it uses:

```sql
-- Load latest snapshot
SELECT ordering, event_ordering, timestamp, <payload_column>
FROM snapshots
WHERE partition_key = ?
ORDER BY ordering DESC
LIMIT 1

-- Insert snapshot marker into events (is_snapshot_marker must be TRUE)
INSERT INTO events (partition_key, ordering, timestamp, <payload_column>, is_snapshot_marker)
VALUES (?, ?, ?, ?, TRUE)

-- Insert snapshot row
INSERT INTO snapshots (partition_key, ordering, event_ordering, timestamp, <payload_column>)
VALUES (?, ?, ?, ?, ?)
```

Both inserts must be executed in a single transaction (auto-commit off). If either raises a
unique-constraint violation the transaction must be rolled back and `ConcurrentWriteException`
thrown.

The surrounding `JdbcSnapshotCapableEventStore` handles connection acquisition and exception
mapping. The dialect implements only the SQL-level details.
