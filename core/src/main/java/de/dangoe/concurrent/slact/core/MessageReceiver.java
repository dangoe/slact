package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;

public interface MessageReceiver<M> {

  void onMessage(@NotNull M message);
}
