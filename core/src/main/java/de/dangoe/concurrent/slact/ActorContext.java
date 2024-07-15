package de.dangoe.concurrent.slact;

public interface ActorContext extends ActorRegistry, ActorHandleResolver {

    ActorHandle<?> sender();

    ActorHandle<?> parent();

    ActorHandle<?> self();
}
