package de.dangoe.concurrent.slact.memory;

import org.jetbrains.annotations.NotNull;

public sealed interface PromptResponse permits PromptResponse.Answer, PromptResponse.Failure {
  record Answer(@NotNull String text) implements PromptResponse {}
  record Failure(@NotNull String errorMessage) implements PromptResponse {}
}
