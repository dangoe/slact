package de.dangoe.concurrent.slact.persistence;

import java.io.Serializable;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies the event stream of a single persistent actor instance by combining a stable,
 * business-defined actor type discriminator with a domain-specific entity ID. Both components must
 * be non-empty strings consisting of letters, digits, underscores, or hyphens.
 */
public record PartitionKey(@NotNull String actorType, @NotNull String entityId) implements
    Serializable {

  private static final @NotNull String IDENTIFIER_REGEX = "[a-zA-Z0-9_-]+";

  private static final @NotNull Pattern IDENTIFIER_PATTERN = Pattern.compile(
      "^%s$".formatted(IDENTIFIER_REGEX));

  private static final @NotNull Pattern RAW_KEY_PATTERN = Pattern.compile(
      "^(%s)#(%s)$".formatted(IDENTIFIER_REGEX, IDENTIFIER_REGEX));

  public PartitionKey {
    checkValidIdentifier("Actor type", actorType);
    checkValidIdentifier("Entity ID", entityId);
  }

  /**
   * Returns a single string representation combining {@code actorType} and {@code entityId},
   * suitable for use as a database key. The format is {@code actorType#entityId}.
   */
  public @NotNull String raw() {
    return "%s#%s".formatted(actorType, entityId);
  }

  /**
   * Parses a raw partition key string as produced by {@link #raw()} back into a
   * {@link PartitionKey}. Returns an empty optional if the input is blank, does not match the
   * expected {@code actorType#entityId} format, or contains invalid characters.
   */
  public static @NotNull Optional<@NotNull PartitionKey> parse(final @NotNull String raw) {
    final var matcher = RAW_KEY_PATTERN.matcher(raw.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(new PartitionKey(matcher.group(1), matcher.group(2)));
  }

  private static void checkValidIdentifier(final @NotNull String type,
      final @Nullable String identifier) {
    if (isInvalidIdentifier(identifier)) {
      throw new IllegalArgumentException(
          "%s must be a non-null, non-empty string containing only letters, digits, underscores, or hyphens.".formatted(
              type));
    }
  }

  private static boolean isInvalidIdentifier(final @Nullable String identifier) {
    return identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches();
  }
}
