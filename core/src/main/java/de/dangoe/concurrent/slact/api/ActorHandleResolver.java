package de.dangoe.concurrent.slact.api;

import java.util.Optional;

public interface ActorHandleResolver {

    <M> Optional<ActorHandle<M>> resolve(ActorPath path);
}
