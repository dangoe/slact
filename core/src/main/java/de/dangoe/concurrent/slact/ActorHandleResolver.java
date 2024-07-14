package de.dangoe.concurrent.slact;

import java.io.Serializable;
import java.util.Optional;

public interface ActorHandleResolver {

    <M extends Serializable> Optional<ActorHandle<M>> resolve(ActorPath path);
}
