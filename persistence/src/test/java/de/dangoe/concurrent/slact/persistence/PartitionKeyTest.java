package de.dangoe.concurrent.slact.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serial;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Partition key")
public class PartitionKeyTest {

  @Nested
  @DisplayName("Should not be instantiable with invalid values")
  class ShouldNotBeInstantiable {

    @Test
    @SuppressWarnings("DataFlowIssue")
    @DisplayName("When null value is given")
    void whenNullValueIsGiven() {
      assertThatThrownBy(() -> new PartitionKey("test", null)).isInstanceOf(
          IllegalArgumentException.class).hasMessageContaining(
          "Entity ID must be a non-null, non-empty string containing only letters, digits, underscores, or hyphens.");
    }

    @Test
    @DisplayName("When blank value is given")
    void whenBlankValueIsGiven() {
      assertThatThrownBy(() -> new PartitionKey("test", "")).isInstanceOf(
          IllegalArgumentException.class).hasMessageContaining(
          "Entity ID must be a non-null, non-empty string containing only letters, digits, underscores, or hyphens.");
    }
  }

  @Nested
  @DisplayName("Should be instantiable with valid values")
  class ShouldBeInstantiable {

    @Test
    @DisplayName("When valid value is given")
    void whenValidValueIsGiven() {
      final var key = new PartitionKey("test", "valid-key");
      assertEquals("test#valid-key", key.raw());
    }
  }
}
