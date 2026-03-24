package de.dangoe.concurrent.slact.persistence;

/**
 * Marker interface for snapshot payload types used in snapshotting-capable persistent actors.
 * Implementing this interface is required to use a type as the snapshot parameter {@code S} in
 * {@link EventStore} and {@link PersistentActor}.
 */
public interface SnapshotPayload {

  /**
   * Sentinel type for actors and event stores that do not support snapshotting.
   * Use {@link None#INSTANCE} wherever a concrete snapshot payload type is not applicable.
   */
  enum None implements SnapshotPayload {
    INSTANCE
  }
}
