package de.dangoe.slacktors.lib;

import java.io.Serializable;
import java.util.Optional;

public interface ActorSelector {

    <M extends Serializable> Optional<ActorHandle<M>> select(ActorPath path);
}
