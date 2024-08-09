package de.dangoe.concurrent.slact;

import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageOp;
import de.dangoe.concurrent.slact.ActorContext.PreparedSendMessageWithResponseRequestOp;

public interface SlactContainer extends ActorHandleResolver, ActorSpawner {

  void shutdown() throws Exception;

  boolean isStopped();

  <M> PreparedSendMessageOp<M> send(M message);

  <M, R> PreparedSendMessageWithResponseRequestOp<M, R> requestResponseTo(M message);
}
