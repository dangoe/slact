package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageWithResponseRequestOp;
import org.jetbrains.annotations.NotNull;

public interface SlactContainer extends ActorHandleResolver, ActorSpawner {

  void shutdown() throws Exception;

  boolean isStopped();

  @NotNull
  <M> PreparedSendMessageOp<M> send(@NotNull M message);

  @NotNull
  <M, R> PreparedSendMessageWithResponseRequestOp<M, R> requestResponseTo(@NotNull M message);

  void exterminate(@NotNull ActorHandle<?> actor);
}
