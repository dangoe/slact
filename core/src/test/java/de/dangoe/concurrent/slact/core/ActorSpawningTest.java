// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

import static de.dangoe.concurrent.slact.testkit.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.patterns.actors.FailingOnReceiveActor;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlactTestContainerExtension.class)
@DisplayName("Actor Spawning")
class ActorSpawningTest {

  @Nested
  @DisplayName("given an actor spawned from root")
  class GivenAnActorSpawnedFromRoot {

    @Test
    @DisplayName("when accessing its path, then it should be a direct child of root")
    void whenAccessingPath_thenShouldBeChildOfRoot(final @NotNull SlactTestContainer container) {

      final var actor = container.spawn("actor", () -> new FailingOnReceiveActor<String>());

      assertThat(actor.path()).isEqualTo(ActorPath.root().append("actor"));
    }
  }

  @Nested
  @DisplayName("given a child actor spawned from a parent actor")
  class GivenAChildActorSpawnedFromParent {

    @Test
    @DisplayName("when accessing its path, then it should be a sub-node of the parent actor's path")
    void whenAccessingPath_thenShouldBeSubNodeOfParentPath(
        final @NotNull SlactTestContainer container) {

      final var childActorHandleRef = new AtomicReference<ActorHandle<?>>();

      container.spawn("actor", () -> new FailingOnReceiveActor<String>() {

        @Override
        public void onStart() {
          super.onStart();
          childActorHandleRef.set(
              context().spawn("child-actor", () -> new FailingOnReceiveActor<String>()));
        }
      });

      await().atMost(DEFAULT_TIMEOUT).untilAsserted(
          () -> assertThat(childActorHandleRef.get().path()).isEqualTo(
              ActorPath.root().append("actor").append("child-actor")));
    }
  }
}
