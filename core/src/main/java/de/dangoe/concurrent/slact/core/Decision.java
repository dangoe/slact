package de.dangoe.concurrent.slact.core;

import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the result of a decision that can either be accepted with a value of type T or
 * rejected with a reason of type R. This is a sealed interface with two implementations:
 * {@link Accepted} and {@link Rejected}. The {@link Accepted} implementation contains the accepted
 * value, while the {@link Rejected} implementation contains the reason for rejection. The interface
 * provides methods to check whether the decision was accepted or rejected, and to retrieve the
 * value or reason accordingly.
 *
 * @param <T> The type of the accepted value if the decision is accepted.
 * @param <R> The type of the rejection reason if the decision is rejected.
 */
public sealed interface Decision<T, R> permits Decision.Accepted, Decision.Rejected {

  /**
   * Represents an accepted decision with a value of type T. This implementation of the
   * {@link Decision} interface indicates that the decision was accepted, and contains the accepted
   * value. The {@link #isAccepted()} method returns true for this implementation.
   *
   * @param value The accepted value of type T. This is the value associated with the accepted
   *              decision.
   * @param <T>   The type of the accepted value.
   * @param <R>   The type of the rejection reason (not used in this implementation, but required by
   *              the interface).
   */
  record Accepted<T, R>(@NotNull T value) implements Decision<T, R> {

    @Override
    public boolean isAccepted() {
      return true;
    }
  }

  /**
   * Represents a rejected decision with a reason of type R. This implementation of the
   * {@link Decision} interface indicates that the decision was rejected, and contains the reason
   * for rejection. The {@link #isAccepted()} method returns false for this implementation.
   *
   * @param reason The reason for rejection of type R. This is the value associated with the
   *               rejected decision, explaining why the decision was rejected.
   * @param <T>    The type of the accepted value (not used in this implementation, but required by
   *               the interface).
   * @param <R>    The type of the rejection reason.
   */
  record Rejected<T, R>(@NotNull R reason) implements Decision<T, R> {

    @Override
    public boolean isAccepted() {
      return false;
    }
  }

  /**
   * Maps the accepted value of this decision using the provided mapper function if the decision is
   * accepted. If the decision is rejected, it returns the same rejected decision without applying
   * the mapper.
   *
   * @param mapper The function to apply to the accepted value if the decision is accepted. Must not
   *               be <code>null</code>.
   * @return A new decision with the mapped accepted value if this decision is accepted, or the same
   * rejected decision if this decision is rejected.
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
   * Maps the rejection reason of this decision using the provided mapper function if the decision
   * is rejected. If the decision is accepted, it returns the same accepted decision without
   * applying the mapper.
   *
   * @param mapper The function to apply to the rejection reason if the decision is rejected. Must
   *               not be <code>null</code>.
   * @return A new decision with the mapped rejection reason if this decision is rejected, or the
   * same accepted decision if this decision is accepted.
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
   * @return <code>true</code> if the decision is accepted, <code>false</code> otherwise.
   * @see Decision#isRejected()
   */
  boolean isAccepted();

  /**
   * Checks whether the decision is rejected.
   *
   * @return <code>true</code> if the decision is rejected, <code>false</code> otherwise.
   * @see Decision#isAccepted()
   */
  default boolean isRejected() {
    return !isAccepted();
  }

  /**
   * Creates a new accepted decision with the given value.
   *
   * @param value The value to be accepted. Must not be <code>null</code>.
   * @param <T>   The type of the accepted value.
   * @param <R>   The type of the rejection reason (not used in this method, but required by the
   *              interface).
   * @return A new instance of {@link Accepted} containing the accepted value.
   */
  static <T, R> @NotNull Decision<T, R> accept(@NotNull T value) {
    return new Accepted<>(value);
  }

  /**
   * Creates a new rejected decision with the given reason.
   *
   * @param reason The reason for rejection. Must not be <code>null</code>.
   * @param <T>    The type of the accepted value (not used in this method, but required by the
   *               interface).
   * @param <R>    The type of the rejection reason.
   * @return A new instance of {@link Rejected} containing the rejection reason.
   */
  static <T, R> @NotNull Decision<T, R> reject(@NotNull R reason) {
    return new Rejected<>(reason);
  }
}
