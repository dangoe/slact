package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A singleton holder for the persistence extension, allowing registration and retrieval of the
 * extension instance. This class ensures that only one instance of the persistence extension can be
 * registered at a time and provides methods to access it safely.
 */
public final class PersistenceExtensionHolder {

  private static final @NotNull PersistenceExtensionHolder instance = new PersistenceExtensionHolder();

  private final @NotNull AtomicReference<@Nullable PersistenceExtension> extensionRef = new AtomicReference<>();

  private PersistenceExtensionHolder() {
    // Private constructor to prevent external instantiation.
  }

  /**
   * Returns the singleton instance of this holder.
   *
   * @return the singleton {@link PersistenceExtensionHolder}.
   */
  public static @NotNull PersistenceExtensionHolder getInstance() {
    return instance;
  }

  /**
   * Registers a persistence extension.
   *
   * @param extension the persistence extension to register; must not be {@code null}.
   * @throws PersistenceException if an extension is already registered.
   */
  public void register(final @NotNull PersistenceExtension extension) {

    Objects.requireNonNull(extension, "Extension to be set must not be null!");

    if (!extensionRef.compareAndSet(null, extension)) {
      throw new PersistenceException(
          "A persistence extension is already registered. Call clear() first.");
    }
  }

  /**
   * Clears the registered persistence extension, allowing a new one to be registered. After calling
   * this method, there will be no registered extension until a new one is registered using the
   * {@link #register} method.
   */
  public void clear() {
    extensionRef.set(null);
  }

  /**
   * Retrieves the currently registered persistence extension, if available.
   *
   * @return an {@link Optional} containing the registered persistence extension, or empty if none
   * is registered.
   */
  public @NotNull Optional<PersistenceExtension> get() {
    return Optional.ofNullable(extensionRef.get());
  }

  /**
   * Retrieves the registered persistence extension, throwing if none is registered. Intentionally
   * package-private: call {@link #get()} from external code.
   *
   * @return the registered persistence extension.
   * @throws PersistenceException if no persistence extension is registered.
   */
  @NotNull PersistenceExtension require() {

    final var ext = extensionRef.get();

    if (ext == null) {
      throw new PersistenceException("No persistence extension registered.");
    }

    return ext;
  }
}
