package de.dangoe.concurrent.slact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActorCreationTest {

    private static final class TestActor extends Actor<String> {

        @Override
        protected void onMessage(String message) {
            throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
        }
    }

    private static final class TestChildActor extends Actor<String> {

        @Override
        protected void onMessage(String message) {
            throw new UnsupportedOperationException("Unimplemented method 'onMessage'");
        }
    }

    private final Slact slact = Slact.createRuntime();

    @Test
    void directorPathShouldBeRootPath() {
        assertThat(slact.path()).isSameAs(ActorPath.root());
    }

    @Test
    void actorPathShouldBeChildOfRoot() {
        final var actor = slact.register("actor", TestActor::new);

        assertThat(actor.path()).isEqualTo(ActorPath.root().append("actor"));
    }

    @Test
    void childActorPathShouldBeSubNodeOfParentActorPath() {
        final var actor = slact.register("actor", TestActor::new);
        final var childActor = actor.register("child-actor", TestChildActor::new);

        assertThat(childActor.path()).isEqualTo(ActorPath.root().append("actor").append("child-actor"));
    }
}
