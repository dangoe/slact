// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.testkit.PersistentActorSpec;
import java.time.Clock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Given a persistent actor")
public class PersistentActorTest extends PersistentActorSpec {

  @Override
  protected @NotNull EventStore createEventStore() {
    return new InMemoryEventStore(Clock.systemUTC());
  }
}

