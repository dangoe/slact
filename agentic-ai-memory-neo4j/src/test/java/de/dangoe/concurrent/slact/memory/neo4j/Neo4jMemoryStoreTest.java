package de.dangoe.concurrent.slact.memory.neo4j;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

@DisplayName("Neo4jMemoryStore")
class Neo4jMemoryStoreTest {

  private static final Driver MOCK_DRIVER = mock(Driver.class);
  private static final String DATABASE = "neo4j";
  private static final int DIMENSION = 3;

  @Nested
  @DisplayName("constructor(Driver, String, int, double)")
  class Constructor {

    @Nested
    @DisplayName("given a similarity threshold below 0.0")
    class GivenThresholdBelowZero {

      @Test
      @DisplayName("when constructed, then throws IllegalArgumentException")
      void whenConstructed_thenThrowsIllegalArgumentException() {
        // Given
        final double invalidThreshold = -0.1;

        // When / Then
        assertThatThrownBy(() ->
            new Neo4jMemoryStore(MOCK_DRIVER, DATABASE, DIMENSION, invalidThreshold))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("-0.1");
      }
    }

    @Nested
    @DisplayName("given a similarity threshold above 1.0")
    class GivenThresholdAboveOne {

      @Test
      @DisplayName("when constructed, then throws IllegalArgumentException")
      void whenConstructed_thenThrowsIllegalArgumentException() {
        // Given
        final double invalidThreshold = 1.1;

        // When / Then
        assertThatThrownBy(() ->
            new Neo4jMemoryStore(MOCK_DRIVER, DATABASE, DIMENSION, invalidThreshold))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1.1");
      }
    }

    @Nested
    @DisplayName("given a similarity threshold of exactly 0.0")
    class GivenThresholdAtZero {

      @Test
      @DisplayName("when constructed, then succeeds")
      void whenConstructed_thenSucceeds() {
        // Given
        final double threshold = 0.0;

        // When / Then
        assertThatNoException().isThrownBy(() ->
            new Neo4jMemoryStore(MOCK_DRIVER, DATABASE, DIMENSION, threshold));
      }
    }

    @Nested
    @DisplayName("given a similarity threshold of exactly 1.0")
    class GivenThresholdAtOne {

      @Test
      @DisplayName("when constructed, then succeeds")
      void whenConstructed_thenSucceeds() {
        // Given
        final double threshold = 1.0;

        // When / Then
        assertThatNoException().isThrownBy(() ->
            new Neo4jMemoryStore(MOCK_DRIVER, DATABASE, DIMENSION, threshold));
      }
    }

    @Nested
    @DisplayName("given a null driver")
    class GivenNullDriver {

      @Test
      @DisplayName("when constructed, then throws NullPointerException")
      void whenConstructed_thenThrowsNullPointerException() {
        // Given / When / Then
        assertThatThrownBy(() ->
            new Neo4jMemoryStore(null, DATABASE, DIMENSION, 0.5))
            .isInstanceOf(NullPointerException.class);
      }
    }

    @Nested
    @DisplayName("given a null database")
    class GivenNullDatabase {

      @Test
      @DisplayName("when constructed, then throws NullPointerException")
      void whenConstructed_thenThrowsNullPointerException() {
        // Given / When / Then
        assertThatThrownBy(() ->
            new Neo4jMemoryStore(MOCK_DRIVER, null, DIMENSION, 0.5))
            .isInstanceOf(NullPointerException.class);
      }
    }
  }
}
