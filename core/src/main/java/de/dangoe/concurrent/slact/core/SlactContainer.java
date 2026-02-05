package de.dangoe.concurrent.slact.core;

public interface SlactContainer extends ActorRuntime, AutoCloseable {

  void shutdown() throws Exception;

  boolean isStopped();
}
