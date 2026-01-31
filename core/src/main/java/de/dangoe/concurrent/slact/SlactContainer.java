package de.dangoe.concurrent.slact;

public interface SlactContainer extends ActorRuntime {

  void shutdown() throws Exception;

  boolean isStopped();
}
