# Event Log — Implementation Specification

This document specifies the data model, storage contract, and required operations for the
event log. It is intentionally database-agnostic so that it can guide the implementation of
persistence dialects for any relational or document store.

---

## Domain Model

### `PartitionKey`

A non-blank string that identifies an actor's event stream. Each actor owns exactly one
partition; no two actors share a partition key.

### `EventEnvelope<E>`

A read-only container returned by the store after a successful write or load.

| Field       | Type      | Description                                                       |
|-------------|-----------|-------------------------------------------------------------------|
| `ordering`  | `long`    | Monotonically increasing position within the partition            |
| `timestamp` | `Instant` | Wall-clock time at which the event was persisted                  |
| `event`     | `E`       | The domain event payload                                          |

`ordering` is **partition-local**: each partition has its own independent sequence starting
from `0`. The same ordering value may exist in multiple partitions without conflict.

---

## Schema

### `events` Table

| Column               | Type                        | Constraints                  |
|----------------------|-----------------------------|------------------------------|
| `partition_key`      | `VARCHAR(255)`              | NOT NULL                     |
| `ordering`           | `BIGINT`                    | NOT NULL                     |
| `timestamp`          | `TIMESTAMP WITH TIME ZONE`  | NOT NULL                     |
| `payload`            | binary / blob               | NOT NULL                     |
| `is_snapshot_marker` | `BOOLEAN`                   | NOT NULL, DEFAULT `false`    |

**Primary key:** `(partition_key, ordering)` (partition-local — the same ordering value may exist in different partitions).

> The payload column may be named differently per dialect implementation (e.g. the PostgreSQL
> dialect uses `snapshot` as the physical column name). The logical name used throughout this
> specification is `payload`.

**Index:** `partition_key` to support efficient partition-scoped queries.

> The `is_snapshot_marker` column is a concern of the [snapshotting mechanism](snapshotting-mechanism.md).
> Plain event stores never set it to `true`. It defaults to `false` so that non-snapshot dialects
> do not need to be aware of it at all. The column must exist on the table because the schema is
> shared between both store variants.

---

## Ordering

Ordering values are **partition-local**: each partition maintains its own independent
sequence. Two events in different partitions may have the same ordering value without any
conflict.

The sentinel value `-1` means _no events written yet_ for a partition (`lastMaxOrdering = -1`).
The first event written to a fresh partition receives `ordering = 0`.

The current `lastMaxOrdering` for a partition is tracked **in Java memory** by the actor (or
the event store wrapper). The store computes the next batch of orderings as
`lastMaxOrdering + 1`, `lastMaxOrdering + 2`, … and writes them directly. There is no
database-side auto-increment; the sequence is managed in application code.

This design enables **optimistic concurrency control**: if another writer has already claimed
ordering `N` in the same partition, the database unique-key constraint on
`(partition_key, ordering)` fires on insert and the store translates the violation into a
`ConcurrentWriteException`.

---

## Required Operations

### `loadEvents(partitionKey, fromOrdering) → List<EventEnvelope<E>>`

Return all events for `partitionKey` whose `ordering >= fromOrdering`, sorted by `ordering`
ascending.

**Filter requirement:** Rows where `is_snapshot_marker = true` must **never** appear in the
result, regardless of `fromOrdering`. This filter must be applied at the storage layer (e.g. as
a SQL predicate), not in application code, so that it applies uniformly to all callers.

**Convenience overload:** `loadEvents(partitionKey)` is equivalent to
`loadEvents(partitionKey, 0)` — it loads the full event history.

### `appendMultiple(partitionKey, lastMaxOrdering, events) → List<EventEnvelope<E>>`

Persist one or more events atomically and return their envelopes.

**Preconditions:**
- `events` must be non-empty (implementations may enforce this).
- `lastMaxOrdering` is the caller's last known maximum ordering for the partition.

**Assignment of orderings:** The first event receives `ordering = lastMaxOrdering + 1`, the
second `lastMaxOrdering + 2`, and so on.

**Atomicity:** All events in the batch must be written in a single database transaction. If any
write fails (including a concurrency violation), the entire batch is rolled back.

**Concurrency:** If any of the computed orderings is already occupied (by a concurrent writer
or a snapshot marker) **within the same partition**, a unique-constraint violation occurs on
`(partition_key, ordering)`. The store must catch this and re-throw it as
`ConcurrentWriteException`. The actor is then responsible for reloading its state and
retrying.

**Returned envelopes** must contain the database-confirmed `timestamp` and the assigned
`ordering` for each event, in insertion order.

**Convenience overload:** `append(partitionKey, lastMaxOrdering, event)` is equivalent to
`appendMultiple` with a single-element list and returns the single resulting envelope.

---

## Serialisation

The `payload` column stores the binary-serialised event. The serialisation format is an
implementation detail of the dialect:

- The current JDBC implementation uses Java's built-in `ObjectOutputStream` /
  `ObjectInputStream`. This requires event classes to implement `Serializable`.
- Future dialects may substitute a different format (e.g. JSON, Protobuf, Avro).
- The dialect is solely responsible for serialising on write and deserialising on read; the
  `EventStore` interface and the actor layer are format-agnostic.

---

## Error Handling

| Condition                                        | Exception                  |
|--------------------------------------------------|----------------------------|
| Ordering slot already occupied by another writer | `ConcurrentWriteException` |
| Any other database error                         | `PersistenceException`     |
| Thread interrupted while waiting for a connection| `PersistenceException` (thread interrupt flag re-set) |

---

## JDBC Dialect Contract (`JdbcDialect`)

When implementing a new JDBC dialect the following method signatures must be fulfilled:

```java
// Load events — must exclude snapshot markers
<E> List<EventEnvelope<E>> loadEvents(
    Connection connection, PartitionKey partitionKey, long fromOrdering) throws SQLException;

// Insert events — must be wrapped in a transaction by the caller (JdbcEventStore)
<E> List<EventEnvelope<E>> insertEvents(
    Connection connection, PartitionKey partitionKey,
    long lastMaxOrdering, List<E> events) throws SQLException, ConcurrentWriteException;
```

The surrounding `JdbcEventStore` handles connection acquisition, transaction management
(begin / commit / rollback), and mapping of exceptions to the public API. The dialect
implements only the SQL-level details.

**Referencing the PostgreSQL dialect:** `PostgreSqlDialect` is the canonical reference
implementation. Key SQL patterns it uses:

```sql
-- Load events (marker exclusion is mandatory)
SELECT ordering, timestamp, <payload_column>
FROM events
WHERE partition_key = ?
  AND ordering >= ?
  AND is_snapshot_marker = FALSE
ORDER BY ordering ASC

-- Insert a single event and return the DB-generated timestamp
INSERT INTO events (partition_key, ordering, timestamp, <payload_column>)
VALUES (?, ?, ?, ?)
RETURNING timestamp
```

The `RETURNING` clause is PostgreSQL-specific. Other databases may require a
`SELECT` after the insert or use `Statement.RETURN_GENERATED_KEYS`.

The unique-key constraint protecting against concurrent writes is on
`(partition_key, ordering)`. Dialects for databases that handle unique violations with a
different exception type must translate it to `ConcurrentWriteException`.
