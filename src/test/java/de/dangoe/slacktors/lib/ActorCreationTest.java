package de.dangoe.slacktors.lib;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ActorCreationTest {

    private static final class TestActor extends AbstractActor<String> {

        @Override
        protected void onMessage(String message) {
            throw new UnsupportedOperationException(
                "Unimplemented method 'onMessage'"
            );
        }
    }

    private static final class TestChildActor extends AbstractActor<String> {

        private final ActorHandle<String> parent;

        public TestChildActor(final ActorHandle<String> parent) {
            super();
            this.parent = parent;
        }

        @Override
        protected void onMessage(String message) {
            throw new UnsupportedOperationException(
                "Unimplemented method 'onMessage'"
            );
        }
    }

    private final Director director = Director.forName("");

    @Test
    void directorPathShouldBeRootPath() {
        assertThat(director.path()).isSameAs(ActorPath.root());
    }

    @Test
    void actorPathShouldBeChildOfRoot() {
        final var actor = director.actorOf("actor", TestActor::new);

        assertThat(actor.path()).isEqualTo(ActorPath.root().append("actor"));
    }

    @Test
    void childActorPathShouldBeSubNodeOfParentActorPath() {
        final var actor = director.actorOf("actor", TestActor::new);
        final var childActor = actor.actorOf("child-actor", () ->
            new TestChildActor(actor)
        );

        assertThat(childActor.path()).isEqualTo(
            ActorPath.root().append("actor").append("child-actor")
        );
    }
}
