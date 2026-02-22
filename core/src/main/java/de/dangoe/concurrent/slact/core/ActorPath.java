package de.dangoe.concurrent.slact.core;

import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the hierarchical path of an actor in the actor system.
 */
public abstract class ActorPath {

  private static final class Root extends ActorPath {

    private static final Root instance = new Root();

    private Root() {
      super();
    }

    /**
     * Returns the parent of the root (always empty).
     *
     * @return Empty optional.
     */
    @Override
    public @NotNull Optional<ActorPath> parent() {
      return Optional.empty();
    }

    /**
     * Appends a child name to the root path.
     *
     * @param name The child name.
     * @return The new actor path.
     */
    @Override
    public @NotNull ActorPath append(@NotNull String name) {
      return new ActorPath.Element(this, name);
    }

    /**
     * Checks equality for root path.
     *
     * @param o The object to compare.
     * @return True if equal.
     */
    @Override
    public boolean equals(final Object o) {
      return o != null && (this == o || getClass() == o.getClass());
    }

    /**
     * Returns hash code for root path.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
      return Root.class.hashCode();
    }

    /**
     * Returns string representation of root path.
     *
     * @return The string "/".
     */
    @Override
    public String toString() {
      return "/";
    }
  }

  /**
   * Element path for actors.
   */
  private static final class Element extends ActorPath {

    private final ActorPath parent;
    private final String name;

    /**
     * Constructs an element actor path.
     *
     * @param parent The parent path.
     * @param name   The actor name.
     */
    public Element(final @NotNull ActorPath parent, final @NotNull String name) {

      super();

      Objects.requireNonNull(parent);
      Objects.requireNonNull(name);

      this.parent = parent;
      this.name = name;
    }

    /**
     * Returns the parent of this actor path.
     *
     * @return Parent actor path.
     */
    @Override
    public @NotNull Optional<ActorPath> parent() {
      return Optional.of(parent);
    }

    /**
     * Appends a child name to this actor path.
     *
     * @param name The child name.
     * @return The new actor path.
     */
    @Override
    public @NotNull ActorPath append(@NotNull String name) {
      return new ActorPath.Element(this, name);
    }

    /**
     * Checks equality for element path.
     *
     * @param o The object to compare.
     * @return True if equal.
     */
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

    /**
     * Returns hash code for element path.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
      return Objects.hash(parent, name);
    }

    /**
     * Returns string representation of element path.
     *
     * @return The string representation.
     */
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
   * @return The root actor path.
   */
  public static @NotNull ActorPath root() {
    return Root.instance;
  }

  /**
   * Appends a child name to this actor path.
   *
   * @param name The child name.
   * @return The new actor path.
   */
  public abstract @NotNull ActorPath append(final @NotNull String name);

  /**
   * Returns the parent of this actor path.
   *
   * @return Parent actor path.
   */
  public abstract @NotNull Optional<ActorPath> parent();

  /**
   * Checks if this actor path is the root path.
   *
   * @return True if this is the root path.
   */
  public boolean isRoot() {
    return parent().isEmpty();
  }
}
