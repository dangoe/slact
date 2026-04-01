package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that forwards a completed {@link PromptResponse} to its intended destination.
 */
public final class AnswerActor extends Actor<AnswerActor.Message> {

  /**
   * Messages handled by {@link AnswerActor}.
   */
  public sealed interface Message permits Message.ForwardAnswer {

    /**
     * Forwards {@code answer} to {@code replyTo}, ignoring {@code originalPrompt}.
     *
     * @param originalPrompt the original prompt text (for context/logging).
     * @param answer         the response to forward.
     * @param replyTo        the destination for the answer.
     */
    record ForwardAnswer(
        @NotNull String originalPrompt,
        @NotNull PromptResponse answer,
        @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }
  }

  @Override
  public void onMessage(final @NotNull Message message) {
    switch (message) {
      case Message.ForwardAnswer forward ->
          send((PromptResponse) forward.answer()).to(forward.replyTo());
    }
  }
}
