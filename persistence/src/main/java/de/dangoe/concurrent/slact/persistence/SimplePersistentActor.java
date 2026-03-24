package de.dangoe.concurrent.slact.persistence;

/**
 * A convenience base class for persistent actors that do not require snapshotting. Extends
 * {@link PersistentActor} with the snapshot type fixed to {@link Void}, avoiding the need to
 * specify a second type parameter in the common case.
 *
 * @param <M> The type of messages that the actor will process.
 * @param <E> The type of domain events that the actor will persist and recover.
 */
public abstract class SimplePersistentActor<M, E> extends
    PersistentActor<M, E, SnapshotPayload.None> {

}
