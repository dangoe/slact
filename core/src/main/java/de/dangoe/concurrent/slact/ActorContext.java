package de.dangoe.concurrent.slact;

import java.io.Serializable;

public interface ActorContext extends ActorFactory, ActorHandleResolver {

    ActorPath parentPath();

    ActorPath selfPath();

    default ActorHandle<?> parent() {
        final var parentPath = this.parentPath();
        return this.resolve(parentPath).orElseThrow(() -> new IllegalStateException("Failed to resolve actor handle for '%s'.".formatted(parentPath)));
    }

    default ActorHandle<?> self() {
        final var selfPath = this.selfPath();
        return this.resolve(selfPath).orElseThrow(() -> new IllegalStateException("Failed to resolve actor handle for '%s'.".formatted(selfPath)));
    }
}
