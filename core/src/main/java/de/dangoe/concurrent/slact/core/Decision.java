package de.dangoe.concurrent.slact.core;

import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the outcome of a decision that is either accepted (carrying a value of type {@code T})
 * or rejected (carrying a reason of type {@code R}). Use {@link #accept(Object)} and
 * {@link #reject(Object)} to create instances.
 *
 * @param <T> The type of the accepted value if the decision is accepted.
 * @param <R> The type of the rejection reason if the decision is rejected.
 */
public sealed interface Decision<T, R> permits Decision.Accepted, Decision.Rejected {

  /**
   * An accepted decision carrying its value.
   *
   * @param value the accepted value.
   * @param <T>   the type of the accepted value.
   * @param <R>   the type of the rejection reason (unused in this implementation).
   */
  record Accepted<T, R>(@NotNull T value) implements Decision<T, R> {

    @Override
    public boolean isAccepted() {
      return true;
    }
  }

  /**
   * A rejected decision carrying its reason.
   *
   * @param reason the rejection reason.
   * @param <T>    the type of the accepted value (unused in this implementation).
   * @param <R>    the type of the rejection reason.
   */
  record Rejected<T, R>(@NotNull R reason) implements Decision<T, R> {

    @Override
    public boolean isAccepted() {
      return false;
    }
  }

  /**
   * Maps the accepted value using {@code mapper}; returns the same rejected decision if rejected.
   *
   * @param mapper the function to apply to the accepted value.
   * @return a new decision with the mapped value, or the unchanged rejected decision.
   * @see Decision#mapRejected(Function)
   */
  default @NotNull Decision<T, R> mapAccepted(
      final @NotNull Function<? super T, ? extends T> mapper) {
    if (isAccepted()) {
      return accept(mapper.apply(((Accepted<T, R>) this).value()));
    } else {
      return this;
    }
  }

  /**
   * Maps the rejection reason using {@code mapper}; returns the same accepted decision if
   * accepted.
   *
   * @param mapper the function to apply to the rejection reason.
   * @return a new decision with the mapped reason, or the unchanged accepted decision.
   * @see Decision#mapAccepted(Function)
   */
  default @NotNull Decision<T, R> mapRejected(
      final @NotNull Function<? super R, ? extends R> mapper) {
    if (isRejected()) {
      return reject(mapper.apply(((Rejected<T, R>) this).reason()));
    } else {
      return this;
    }
  }

  /**
   * Checks whether the decision is accepted.
   *
   * @return {@code true} if the decision is accepted, {@code false} otherwise.
   * @see Decision#isRejected()
   */
  boolean isAccepted();

  /**
   * Checks whether the decision is rejected.
   *
   * @return {@code true} if the decision is rejected, {@code false} otherwise.
   * @see Decision#isAccepted()
   */
  default boolean isRejected() {
    return !isAccepted();
  }

  /**
   * Creates a new accepted decision with the given value.
   *
   * @param value the value to accept.
   * @param <T>   the type of the accepted value.
   * @param <R>   the type of the rejection reason.
   * @return a new {@link Accepted} instance.
   */
  static <T, R> @NotNull Decision<T, R> accept(@NotNull T value) {
    return new Accepted<>(value);
  }

  /**
   * Creates a new rejected decision with the given reason.
   *
   * @param reason the rejection reason.
   * @param <T>    the type of the accepted value.
   * @param <R>    the type of the rejection reason.
   * @return a new {@link Rejected} instance.
   */
  static <T, R> @NotNull Decision<T, R> reject(@NotNull R reason) {
    return new Rejected<>(reason);
  }
}
