package de.dangoe.slacktors.lib;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ActorCreationTest {

    private static final class TestActor extends AbstractActor<String> {

        @Override
        protected void onMessage(String message) {
            throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
        }
    }

    private static final class TestChildActor extends AbstractActor<String> {

        @Override
        protected void onMessage(String message) {
            throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
        }
    }

    private final Actors container = Actors.createRuntime();

    @Test
    void directorPathShouldBeRootPath() {
        assertThat(container.path()).isSameAs(ActorPath.root());
    }

    @Test
    void actorPathShouldBeChildOfRoot() {
        final var actor = container.register("actor", TestActor::new);

        assertThat(actor.path()).isEqualTo(ActorPath.root().append("actor"));
    }

    @Test
    void childActorPathShouldBeSubNodeOfParentActorPath() {
        final var actor = container.register("actor", TestActor::new);
        final var childActor = actor.register("child-actor", TestChildActor::new);

        assertThat(childActor.path()).isEqualTo(ActorPath.root().append("actor").append("child-actor"));
    }
}
