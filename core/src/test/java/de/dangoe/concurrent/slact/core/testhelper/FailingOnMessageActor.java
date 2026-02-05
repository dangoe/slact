package de.dangoe.concurrent.slact.core.testhelper;

import de.dangoe.concurrent.slact.core.Actor;
import org.assertj.core.api.Fail;
import org.jetbrains.annotations.NotNull;

public class FailingOnMessageActor<M> extends Actor<M> {

  @Override
  public void onMessage(@NotNull M message) {
    Fail.fail("Expected to receive no message, but received %s".formatted(message));
  }
}
