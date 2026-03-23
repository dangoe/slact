package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A singleton holder for the persistence extension, allowing registration and retrieval of the
 * extension instance. This class ensures that only one instance of the persistence extension can be
 * registered at a time and provides methods to access it safely.
 */
public final class PersistenceExtensionHolder {

  private static final @NotNull PersistenceExtensionHolder instance = new PersistenceExtensionHolder();

  private @Nullable PersistenceExtension extension;

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

    if (this.extension != null) {
      throw new PersistenceException(
          "A persistence extension is already registered. Call clear() first.");
    }

    this.extension = extension;
  }

  /**
   * Clears the registered persistence extension, allowing a new one to be registered. After calling
   * this method, there will be no registered extension until a new one is registered using the
   * <code>register</code> method.
   */
  public void clear() {
    this.extension = null;
  }

  /**
   * Retrieves the currently registered persistence extension, if available.
   *
   * @return An <code>Optional</code> containing the registered persistence extension, or an empty
   * <code>Optional</code> if no extension is currently registered.
   */
  public @NotNull Optional<PersistenceExtension> get() {
    return Optional.ofNullable(extension);
  }

  /**
   * Retrieves the currently registered persistence extension, throwing an exception if no extension
   * is registered.
   *
   * @return The registered persistence extension.
   * @throws PersistenceException Thrown if no persistence extension is registered.
   */
  @NotNull PersistenceExtension require() {

    if (extension == null) {
      throw new PersistenceException("No persistence extension registered.");
    }

    return extension;
  }
}
