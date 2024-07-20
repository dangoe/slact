package de.dangoe.concurrent.slact;

public interface MessageReceiver<M> {

  void onMessage(M message);
}
