package de.dangoe.concurrent.slact;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActiveActorContextHolder {

  private static final @NotNull ActiveActorContextHolder instance = new ActiveActorContextHolder();

  private final @NotNull ThreadLocal<ActorContext> activeContextHolder = new ThreadLocal<>();

  private ActiveActorContextHolder() {
    // prevent initialization
  }

  public static @NotNull ActiveActorContextHolder getInstance() {
    return instance;
  }

  public @NotNull Optional<ActorContext> activeContext() {
    return Optional.ofNullable(this.activeContextHolder.get());
  }

  public void activateContext(final @NotNull ActorContext context) {
    this.activeContextHolder.set(context);
  }

  public void deactivateContext() {
    this.activeContextHolder.remove();
  }
}
