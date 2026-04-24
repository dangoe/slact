// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.testkit;

import java.time.Duration;

/**
 * A utility class that holds constants used across the test suite.
 */
public class Constants {

  /**
   * The default timeout duration for awaiting conditions in tests.
   */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  private Constants() {
    // prevent instantiation
  }
}
