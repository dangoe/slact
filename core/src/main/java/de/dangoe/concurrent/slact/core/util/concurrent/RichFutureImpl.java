// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

class RichFutureImpl<T> implements RichFuture<T> {

  private final @NotNull CompletableFuture<T> delegate;

  public RichFutureImpl(final @NotNull CompletableFuture<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return delegate.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return delegate.isCancelled();
  }

  @Override
  public boolean isDone() {
    return delegate.isDone();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    return delegate.get();
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.get(timeout, unit);
  }

  @Override
  public @NotNull <S> RichFuture<S> thenApply(
      final @NotNull Function<? super T, ? extends S> mapper) {
    return new RichFutureImpl<>(delegate.thenApply(mapper));
  }

  @Override
  public @NotNull <S> RichFuture<S> thenCompose(
      @NotNull Function<? super T, ? extends RichFuture<S>> mapper) {
    return new RichFutureImpl<>(
        delegate.thenCompose(it -> ((RichFutureImpl<S>) mapper.apply(it)).delegate));
  }

  @Override
  public @NotNull RichFuture<T> exceptionally(final @NotNull Function<Throwable, ? extends T> fn) {
    return new RichFutureImpl<>(delegate.exceptionally(fn));
  }

  @Override
  public T join() {
    return delegate.join();
  }
}
