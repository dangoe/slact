package de.dangoe.concurrent.slact;

interface ActorRegistry {

  void register(final ActorWrapper<?> actor);

  void unregister(final ActorWrapper<?> actor);
}
