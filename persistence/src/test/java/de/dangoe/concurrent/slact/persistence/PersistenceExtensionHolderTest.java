// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.dangoe.concurrent.slact.persistence.exception.PersistenceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Persistence extension holder")
public class PersistenceExtensionHolderTest {

  @AfterAll
  static void tearDown() {
    // Ensure that the singleton holder is cleared after all tests to avoid side effects on other tests.
    PersistenceExtensionHolder.getInstance().clear();
  }

  @Nested
  @DisplayName("Registering an extension")
  class Register {

    @Nested
    @DisplayName("Should fail")
    class ShouldFail {

      @Test
      @SuppressWarnings("DataFlowIssue")
      @DisplayName("When null extension is given")
      void whenNullExtensionIsGiven() {

        final var holder = PersistenceExtensionHolder.getInstance();
        holder.clear();

        assertThatThrownBy(() -> holder.register(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Extension to be set must not be null!");
      }

      @Test
      @DisplayName("When extension is already registered")
      void whenExtensionIsAlreadyRegistered() {

        final var holder = PersistenceExtensionHolder.getInstance();
        holder.clear();

        final var extension = mock(PersistenceExtension.class);
        holder.register(extension);

        assertThatThrownBy(() -> holder.register(extension))
            .isInstanceOf(PersistenceException.class)
            .hasMessageContaining(
                "A persistence extension is already registered. Call clear() first.");
      }
    }

    @Nested
    @DisplayName("Should succeed")
    class ShouldSucceed {

      @Test
      @DisplayName("When valid extension is given")
      void whenValidExtensionIsGiven() {

        final var holder = PersistenceExtensionHolder.getInstance();
        holder.clear();

        final var extension = mock(PersistenceExtension.class);
        holder.register(extension);

        assertThat(holder.get()).containsSame(extension);
      }
    }
  }

  @Nested
  @DisplayName("Clearing the holder")
  class Clear {

    @Test
    @DisplayName("Should be callable without registered extension")
    void shouldBeCallableWithoutRegisteredExtension() {

      final var holder = PersistenceExtensionHolder.getInstance();

      holder.clear();

      assertThat(holder.get()).isEmpty();
    }

    @Test
    @DisplayName("Should allow registering a new extension after clearing")
    void shouldRemoveAnExistingRegistration() {

      final var holder = PersistenceExtensionHolder.getInstance();
      holder.clear();

      final var firstExtension = mock(PersistenceExtension.class);
      final var secondExtension = mock(PersistenceExtension.class);

      holder.register(firstExtension);

      holder.clear();

      holder.register(secondExtension);
    }
  }

  @Nested
  @DisplayName("Retrieving the registered extension")
  class Retrieve {

    @Test
    @DisplayName("Should return empty if no extension is registered")
    void shouldReturnEmptyIfNoExtensionIsRegistered() {

      final var holder = PersistenceExtensionHolder.getInstance();
      holder.clear();

      assertThat(holder.get()).isEmpty();
    }

    @Test
    @DisplayName("Should return the registered extension")
    void shouldReturnTheRegisteredExtension() {

      final var holder = PersistenceExtensionHolder.getInstance();
      holder.clear();

      final var extension = mock(PersistenceExtension.class);
      holder.register(extension);

      assertThat(holder.get()).containsSame(extension);
    }

    @Test
    @DisplayName("Should throw and exception if no extension is registered but required")
    void shouldThrowAndExceptionIfNoExtensionIsRegisteredButRequired() {

      final var holder = PersistenceExtensionHolder.getInstance();
      holder.clear();

      assertThatThrownBy(holder::require)
          .isInstanceOf(PersistenceException.class)
          .hasMessageContaining("No persistence extension registered.");
    }

    @Test
    @DisplayName("Should return the registered extension when required")
    void shouldReturnTheRegisteredExtensionWhenRequired() {

      final var holder = PersistenceExtensionHolder.getInstance();
      holder.clear();

      final var extension = mock(PersistenceExtension.class);
      holder.register(extension);

      assertThat(holder.require()).isSameAs(extension);
    }
  }
}