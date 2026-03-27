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

  private record TestPartitionKey(@NotNull String value) implements PartitionKey<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    TestPartitionKey {
      Objects.requireNonNull(value, "Value must not be null!");
      if (value.isBlank()) {
        throw new IllegalArgumentException("Value must not be blank!");
      }
    }

    @Override
    public Class<String> eventType() {
      return String.class;
    }
  }

  @Nested
  @DisplayName("Should not be instantiable with invalid values")
  class ShouldNotBeInstantiable {

    @Test
    @SuppressWarnings("DataFlowIssue")
    @DisplayName("When null value is given")
    void whenNullValueIsGiven() {
      assertThatThrownBy(() -> new TestPartitionKey(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Value must not be null!");
    }

    @Test
    @DisplayName("When blank value is given")
    void whenBlankValueIsGiven() {
      assertThatThrownBy(() -> new TestPartitionKey(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Value must not be blank!");
    }
  }

  @Nested
  @DisplayName("Should be instantiable with valid values")
  class ShouldBeInstantiable {

    @Test
    @DisplayName("When valid value is given")
    void whenValidValueIsGiven() {
      final var key = new TestPartitionKey("valid-key");
      assertEquals("valid-key", key.value());
    }
  }
}
