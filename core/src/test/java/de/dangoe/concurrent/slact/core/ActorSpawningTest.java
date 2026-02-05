package de.dangoe.concurrent.slact.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActorSpawningTest {

  private static final class TestActor extends Actor<String> {

    @Override
    public void onMessage(@NotNull String message) {
      throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
    }
  }

  private static final class TestChildActor extends Actor<String> {

    @Override
    public void onMessage(@NotNull String message) {
      throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
    }
  }

  private final SlactContainer container = new SlactContainerBuilder().build();

  @Test
  void actorPathShouldBeChildOfRoot() {
    final var actor = container.spawn("actor", TestActor::new);

    assertThat(actor.path()).isEqualTo(ActorPath.root().append("actor"));
  }

  @Test
  void childActorPathShouldBeSubNodeOfParentActorPath() {
    final var actor = container.spawn("actor", TestActor::new);
    final var childActor = actor.spawn("child-actor", TestChildActor::new);

    assertThat(childActor.path()).isEqualTo(ActorPath.root().append("actor").append("child-actor"));
  }
}
