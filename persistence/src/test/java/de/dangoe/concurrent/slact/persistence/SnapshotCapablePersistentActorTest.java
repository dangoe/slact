// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.persistence;

import de.dangoe.concurrent.slact.persistence.testkit.SnapshotCapablePersistentActorSpec;
import java.time.Clock;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Given a snapshot capable persistent actor")
public class SnapshotCapablePersistentActorTest extends SnapshotCapablePersistentActorSpec {

  @Override
  protected @NotNull SnapshotCapableEventStore createEventStore() {
    return new SnapshotCapableInMemoryEventStore(Clock.systemUTC());
  }
}

