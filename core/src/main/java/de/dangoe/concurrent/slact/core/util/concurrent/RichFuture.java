// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Enriched {@link Future} that adds monadic composition operators for chaining async computations.
 * All derived futures are backed by the same thread pool as the original; use {@link #of} to wrap a
 * {@link java.util.concurrent.CompletableFuture}.
 *
 * @param <T> the result type.
 */
public interface RichFuture<T> extends Future<T> {

  /**
   * Returns a new future that applies {@code mapper} to this future's result when it completes.
   *
   * @param mapper the function to apply to the result.
   * @param <S>    the mapped result type.
   * @return a new {@link RichFuture} carrying the mapped value.
   */
  <S> @NotNull RichFuture<S> thenApply(final @NotNull Function<? super T, ? extends S> mapper);

  /**
   * Returns a new future produced by applying {@code mapper} to this future's result.
   *
   * @param mapper the function that produces the next future stage.
   * @param <S>    the result type of the composed future.
   * @return a new {@link RichFuture} that flattens the nested future.
   */
  <S> @NotNull RichFuture<S> thenCompose(
      final @NotNull Function<? super T, ? extends RichFuture<S>> mapper);

  /**
   * Returns a new future that uses {@code fn} to recover from a failure in this stage.
   *
   * @param fn function that maps the thrown exception to a fallback value.
   * @return a new {@link RichFuture} that never completes exceptionally when {@code fn} succeeds.
   */
  @NotNull RichFuture<T> exceptionally(final @NotNull Function<Throwable, ? extends T> fn);

  /**
   * Blocks until this future completes and returns the result, throwing an unchecked exception on
   * failure.
   *
   * @return the computed result.
   */
  T join();

  /**
   * Wraps a {@link CompletableFuture} in a {@link RichFuture}.
   *
   * @param delegate the completable future to wrap.
   * @param <T>      the result type.
   * @return a {@link RichFuture} backed by {@code delegate}.
   */
  static <T> @NotNull RichFuture<T> of(final @NotNull CompletableFuture<T> delegate) {
    return new RichFutureImpl<>(delegate);
  }
}
