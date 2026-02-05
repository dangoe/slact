package de.dangoe.concurrent.slact.core;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public interface ActorHandleResolver {

  @NotNull
  <M> Optional<ActorHandle<M>> resolve(@NotNull ActorPath path);
}
