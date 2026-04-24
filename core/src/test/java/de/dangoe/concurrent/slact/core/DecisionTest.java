// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Decision Unit Tests")
class DecisionTest {

  @Nested
  @DisplayName("Accepted")
  class AcceptedTests {

    final @NotNull Decision<String, Object> accepted = Decision.accept("foo");

    @Test
    @DisplayName("isAccepted returns true")
    void isAcceptedReturnsTrue() {
      assertThat(accepted.isAccepted()).isTrue();
    }

    @Test
    @DisplayName("isRejected returns false")
    void isRejectedReturnsFalse() {
      assertThat(accepted.isRejected()).isFalse();
    }


    @Test
    @DisplayName("mapAccepted applies mapper")
    void mapAcceptedAppliesMapper() {

      final var mapped = accepted.mapAccepted("%sbar"::formatted);

      assertThat(mapped.isAccepted()).isTrue();
      assertThat(mapped).isEqualTo(Decision.accept("foobar"));
    }

    @Test
    @DisplayName("mapRejected does not apply mapper")
    void mapRejectedDoesNotApplyMapper() {

      final var mapped = accepted.mapRejected(it -> "should not be applied");

      assertThat(mapped.isAccepted()).isTrue();
      assertThat(mapped).isEqualTo(accepted);
    }
  }

  @Nested
  @DisplayName("Rejected")
  class RejectedTests {

    final @NotNull Decision<Object, String> rejected = Decision.reject("reason");

    @Test
    @DisplayName("isAccepted returns false")
    void isAcceptedReturnsFalse() {
      assertThat(rejected.isAccepted()).isFalse();
    }

    @Test
    @DisplayName("isRejected returns true")
    void isRejectedReturnsTrue() {
      assertThat(rejected.isRejected()).isTrue();
    }

    @Test
    @DisplayName("mapAccepted does not apply mapper")
    void mapAcceptedDoesNotApplyMapper() {

      final var mapped = rejected.mapAccepted(it -> "should not be applied");

      assertThat(mapped.isRejected()).isTrue();
      assertThat(mapped).isEqualTo(rejected);
    }

    @Test
    @DisplayName("mapRejected applies mapper")
    void mapRejectedAppliesMapper() {

      final var mapped = rejected.mapRejected(reason -> reason + " mapped");

      assertThat(mapped.isRejected()).isTrue();
      assertThat(mapped).isEqualTo(Decision.reject("reason mapped"));
    }
  }
}

