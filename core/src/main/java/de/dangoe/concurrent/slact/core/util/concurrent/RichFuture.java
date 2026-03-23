package de.dangoe.concurrent.slact.core.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public interface RichFuture<T> extends Future<T> {

  <S> @NotNull RichFuture<S> thenApply(final @NotNull Function<? super T, ? extends S> mapper);

  @NotNull RichFuture<T> exceptionally(final @NotNull Function<Throwable, ? extends T> fn);

  T join();

  static <T> @NotNull RichFuture<T> of(final @NotNull CompletableFuture<T> delegate) {
    return new RichFutureImpl<>(delegate);
  }
}
