package de.dangoe.slacktors.lib;

import java.util.Objects;
import java.util.Optional;

public abstract class ActorPath {

    private static final class Root extends ActorPath {

        private Root() {
            super();
        }

        @Override
        public Optional<ActorPath> parent() {
            return Optional.empty();
        }

        @Override
        public ActorPath append(String name) {
            return new ActorPath.Element(this, name);
        }

        @Override
        public boolean equals(final Object o) {
            return o != null && (this == o || getClass() == o.getClass());
        }

        @Override
        public int hashCode() {
            return Root.class.hashCode();
        }

        @Override
        public String toString() {
            return "/";
        }
    }

    private static final class Element extends ActorPath {

        private final ActorPath parent;

        private final String name;

        public Element(final ActorPath parent, final String name) {
            super();
            this.parent = parent;
            this.name = name;
        }

        @Override
        public Optional<ActorPath> parent() {
            if (parent instanceof Root) {
                return Optional.empty();
            }
            return Optional.of(parent);
        }

        @Override
        public ActorPath append(String name) {
            return new ActorPath.Element(this, name);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Element element = (Element) o;
            return (Objects.equals(parent, element.parent) && Objects.equals(name, element.name));
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, name);
        }

        @Override
        public String toString() {
            if (parent == root) {
                return "/%s".formatted(name);
            } else {
                return "%s/%s".formatted(parent, name);
            }
        }
    }

    private static final ActorPath root = new Root();

    public static ActorPath root() {
        return root;
    }

    public abstract ActorPath append(final String name);

    public abstract Optional<ActorPath> parent();
}
