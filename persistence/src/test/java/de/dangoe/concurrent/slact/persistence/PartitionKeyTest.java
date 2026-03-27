package de.dangoe.concurrent.slact.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
      assertThatThrownBy(() -> new PartitionKey<>(String.class, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Value must not be null!");
    }

    @Test
    @DisplayName("When blank value is given")
    void whenBlankValueIsGiven() {
      assertThatThrownBy(() -> new PartitionKey<>(String.class, ""))
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
      final var key = new PartitionKey<>(String.class, "valid-key");
      assertEquals("valid-key", key.value());
    }
  }
}