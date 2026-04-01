package de.dangoe.concurrent.slact.ai.memory;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the response to a prompt, which can either be a successful answer or a failure with an
 * error message.
 */
public sealed interface PromptResponse permits PromptResponse.Answer, PromptResponse.Failure {

  /**
   * Represents a successful response containing the answer text.
   *
   * @param text the answer text
   */
  record Answer(@NotNull String text) implements PromptResponse {

  }

  /**
   * Represents a failure response containing an error message.
   *
   * @param errorMessage a human-readable description of the failure
   */
  record Failure(@NotNull String errorMessage) implements PromptResponse {

  }
}
