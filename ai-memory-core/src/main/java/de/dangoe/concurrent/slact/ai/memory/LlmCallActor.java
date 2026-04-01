package de.dangoe.concurrent.slact.ai.memory;

import de.dangoe.concurrent.slact.core.Actor;
import de.dangoe.concurrent.slact.core.ActorHandle;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Actor that calls the target language model with an enriched prompt and delivers the response to a
 * caller-supplied reply handle.
 */
public final class LlmCallActor extends Actor<LlmCallActor.Message> {

  /**
   * Messages handled by {@link LlmCallActor}.
   */
  public sealed interface Message permits Message.Call, Message.CallDone, Message.CallFailed {

    /**
     * Initiates an LLM call with the given enriched prompt; the response will be forwarded to
     * {@code replyTo}.
     *
     * @param enrichedPrompt the context-enriched prompt.
     * @param replyTo        where to deliver the resulting {@link PromptResponse}.
     */
    record Call(@NotNull String enrichedPrompt,
                @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    /**
     * Internal message carrying the completed LLM response.
     *
     * @param response the raw response text from the LLM.
     * @param replyTo  the original reply target.
     */
    record CallDone(@NotNull String response,
                    @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }

    /**
     * Internal message indicating the LLM call failed.
     *
     * @param errorMessage a description of the failure.
     * @param replyTo      the original reply target.
     */
    record CallFailed(@NotNull String errorMessage, @NotNull Throwable cause,
                      @NotNull ActorHandle<PromptResponse> replyTo) implements Message {

    }
  }

  private final @NotNull TargetModelPort targetModelPort;

  /**
   * Creates a new {@link LlmCallActor}.
   *
   * @param targetModelPort the port used to complete prompts; must not be {@code null}.
   */
  public LlmCallActor(final @NotNull TargetModelPort targetModelPort) {
    this.targetModelPort = Objects.requireNonNull(targetModelPort,
        "TargetModelPort must not be null");
  }

  @Override
  public void onMessage(final @NotNull Message message) {
    switch (message) {
      case Message.Call call -> handleCall(call);
      case Message.CallDone done ->
          send((PromptResponse) new PromptResponse.Answer(done.response())).to(done.replyTo());
      case Message.CallFailed failed -> send(
          (PromptResponse) new PromptResponse.Failure(failed.errorMessage(), failed.cause())).to(
          failed.replyTo());
    }
  }

  private void handleCall(final @NotNull Message.Call call) {
    final var future = targetModelPort.complete(call.enrichedPrompt())
        .thenApply(response -> (Message) new Message.CallDone(response, call.replyTo()))
        .exceptionally(e -> new Message.CallFailed("LLM call failed", e, call.replyTo()));
    pipeFuture(future).to(self());
  }
}
