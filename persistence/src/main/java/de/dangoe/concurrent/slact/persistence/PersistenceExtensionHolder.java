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

  public static @NotNull PersistenceExtensionHolder getInstance() {
    return instance;
  }

  /**
   * Registers a persistence extension.
   *
   * @param extension The persistence extension to be registered. Must not be <code>null</code>.
   * @throws PersistenceException Thrown if an extension is already registered.
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
   * <code>register</code> method.
   */
  public void clear() {
    extensionRef.set(null);
  }

  /**
   * Retrieves the currently registered persistence extension, if available.
   *
   * @return An <code>Optional</code> containing the registered persistence extension, or an empty
   * <code>Optional</code> if no extension is currently registered.
   */
  public @NotNull Optional<PersistenceExtension> get() {
    return Optional.ofNullable(extensionRef.get());
  }

  /**
   * Retrieves the currently registered persistence extension, throwing an exception if no extension
   * is registered.
   *
   * <p>Intentionally package-private: persistent actors within this package call this on every
   * message dispatch, so the method is kept internal to avoid leaking the "require or throw"
   * contract into the public API. External callers should use {@link #get()} instead.
   *
   * @return The registered persistence extension.
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
