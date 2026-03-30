package de.dangoe.concurrent.slact.core;

import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the hierarchical path of an actor in the actor system.
 */
public abstract class ActorPath {

  /** Creates a new actor path. */
  protected ActorPath() {
    super();
  }

  private static final class Root extends ActorPath {

    private static final Root instance = new Root();

    private Root() {
      super();
    }

    @Override
    public @NotNull Optional<ActorPath> parent() {
      return Optional.empty();
    }

    @Override
    public @NotNull ActorPath append(@NotNull String name) {
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

    public Element(final @NotNull ActorPath parent, final @NotNull String name) {

      super();

      Objects.requireNonNull(parent);
      Objects.requireNonNull(name);

      this.parent = parent;
      this.name = name;
    }

    @Override
    public @NotNull Optional<ActorPath> parent() {
      return Optional.of(parent);
    }

    @Override
    public @NotNull ActorPath append(@NotNull String name) {
      return new ActorPath.Element(this, name);
    }

    @Override
    public boolean equals(final Object o) {

      if (this == o) {
        return true;
      } else if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final var element = (Element) o;

      return Objects.equals(parent, element.parent) && Objects.equals(name, element.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(parent, name);
    }

    @Override
    public String toString() {
      if (parent.isRoot()) {
        return "/%s".formatted(name);
      } else {
        return "%s/%s".formatted(parent, name);
      }
    }
  }


  /**
   * Returns the root actor path.
   *
   * @return the root actor path.
   */
  public static @NotNull ActorPath root() {
    return Root.instance;
  }

  /**
   * Appends a child name to this actor path.
   *
   * @param name the child name.
   * @return the new actor path.
   */
  public abstract @NotNull ActorPath append(final @NotNull String name);

  /**
   * Returns the parent of this actor path.
   *
   * @return the parent actor path.
   */
  public abstract @NotNull Optional<ActorPath> parent();

  /**
   * Checks if this actor path is the root path.
   *
   * @return {@code true} if this is the root path.
   */
  public boolean isRoot() {
    return parent().isEmpty();
  }
}
