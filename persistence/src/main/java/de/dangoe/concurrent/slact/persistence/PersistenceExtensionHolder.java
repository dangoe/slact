package de.dangoe.concurrent.slact.persistence;

import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PersistenceExtensionHolder {

  private static final @NotNull PersistenceExtensionHolder instance = new PersistenceExtensionHolder();

  private @Nullable PersistenceExtension extension;

  private PersistenceExtensionHolder() {
    // Private constructor to prevent external instantiation.
  }

  public static @NotNull PersistenceExtensionHolder getInstance() {
    return instance;
  }

  public void register(final @NotNull PersistenceExtension extension) {

    Objects.requireNonNull(extension, "Extension to be set must not be null!");

    if (this.extension != null) {
      throw new IllegalStateException(
          "A persistence extension is already registered. Call clear() first.");
    }

    this.extension = extension;
  }

  public void clear() {
    this.extension = null;
  }

  public @NotNull Optional<PersistenceExtension> get() {
    return Optional.ofNullable(extension);
  }

  @NotNull PersistenceExtension require() {

    if (extension == null) {
      throw new IllegalStateException("No persistence extension registered.");
    }

    return extension;
  }
}
