package de.dangoe.concurrent.slact.core;

import de.dangoe.concurrent.slact.testkit.SlactTestContainer;
import de.dangoe.concurrent.slact.testkit.SlactTestContainerExtension;
import de.dangoe.concurrent.slact.testkit.patterns.actors.FailingOnReceiveActor;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static de.dangoe.concurrent.slact.core.testhelper.Constants.DEFAULT_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SlactTestContainerExtension.class)
class ActorSpawningTest {

  @Test
  void actorPathShouldBeChildOfRoot(final @NotNull SlactTestContainer container) {

    final var actor = container.spawn("actor", () -> new FailingOnReceiveActor<String>());

    assertThat(actor.path()).isEqualTo(ActorPath.root().append("actor"));
  }

  @Test
  void childActorPathShouldBeSubNodeOfParentActorPath(final @NotNull SlactTestContainer container) {

    final var childActorHandleRef = new AtomicReference<ActorHandle<?>>();

    container.spawn("actor", () -> new FailingOnReceiveActor<String>() {

      @Override
      public void onStart() {
        super.onStart();
        childActorHandleRef.set(
            context().spawn("child-actor", () -> new FailingOnReceiveActor<String>()));
      }
    });

    await().atMost(DEFAULT_TIMEOUT).untilAsserted(() -> {
      assertThat(childActorHandleRef.get().path()).isEqualTo(
          ActorPath.root().append("actor").append("child-actor"));
    });
  }
}
