// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ActorPath")
class ActorPathTest {

  @Nested
  @DisplayName("root()")
  class Root {

    @Test
    @DisplayName("when called, then always returns the same singleton instance")
    void whenCalled_thenReturnsSameInstance() {

      final var first = ActorPath.root();
      final var second = ActorPath.root();

      assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("when called, then the returned path is root")
    void whenCalled_thenIsRoot() {
      assertThat(ActorPath.root().isRoot()).isTrue();
    }

    @Test
    @DisplayName("when called, then parent() is empty")
    void whenCalled_thenParentIsEmpty() {
      assertThat(ActorPath.root().parent()).isEmpty();
    }

    @Test
    @DisplayName("when called, then toString() returns \"/\"")
    void whenCalled_thenToStringReturnsSlash() {
      assertThat(ActorPath.root()).hasToString("/");
    }
  }

  @Nested
  @DisplayName("append()")
  class Append {

    @Nested
    @DisplayName("given a valid name appended to root")
    class GivenValidNameOnRoot {

      @Test
      @DisplayName("when appended, then the resulting path is not root")
      void whenAppended_thenResultIsNotRoot() {

        final var root = ActorPath.root();
        final var child = root.append("actor");

        assertThat(child.isRoot()).isFalse();
      }

      @Test
      @DisplayName("when appended, then parent() returns the root path")
      void whenAppended_thenParentIsRoot() {

        final var root = ActorPath.root();
        final var child = root.append("actor");

        assertThat(child.parent()).contains(root);
      }

      @Test
      @DisplayName("when appended, then toString() returns \"/name\"")
      void whenAppended_thenToStringHasLeadingSlash() {

        final var root = ActorPath.root();
        final var child = root.append("actor");

        assertThat(child).hasToString("/actor");
      }
    }

    @Nested
    @DisplayName("given a deeply nested chain of appends")
    class GivenDeepNesting {

      @Test
      @DisplayName("when chained, then toString() reflects the full path hierarchy")
      void whenChained_thenToStringReflectsFullHierarchy() {

        final var path = ActorPath.root()
            .append("system")
            .append("user")
            .append("worker");

        assertThat(path).hasToString("/system/user/worker");
      }

      @Test
      @DisplayName("when chained, then parent() returns the immediate ancestor")
      void whenChained_thenParentIsImmediateAncestor() {

        final var grandparent = ActorPath.root().append("system");
        final var parent = grandparent.append("user");

        final var child = parent.append("worker");

        assertThat(child.parent()).contains(parent);
      }

      @Test
      @DisplayName("when chained three levels deep, then isRoot() is false at every level")
      void whenChained_thenIsRootIsFalseAtEveryLevel() {

        final var level1 = ActorPath.root().append("a");
        final var level2 = level1.append("b");
        final var level3 = level2.append("c");

        assertThat(level1.isRoot()).isFalse();
        assertThat(level2.isRoot()).isFalse();
        assertThat(level3.isRoot()).isFalse();
      }
    }

    @Nested
    @DisplayName("given a null name")
    class GivenNullName {

      @Test
      @DisplayName("when null is passed, then throws NullPointerException")
      void whenNullPassed_thenThrowsNullPointerException() {

        final var root = ActorPath.root();

        //noinspection DataFlowIssue
        assertThatThrownBy(() -> root.append(null))
            .isInstanceOf(NullPointerException.class);
      }
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsAndHashCode {

    @Nested
    @DisplayName("given two paths built with identical segments")
    class GivenIdenticalPaths {

      @Test
      @DisplayName("when compared, then they are equal")
      void whenCompared_thenEqual() {

        final var firstPath = ActorPath.root().append("actor").append("child");
        final var secondPath = ActorPath.root().append("actor").append("child");

        assertThat(firstPath).isEqualTo(secondPath);
      }

      @Test
      @DisplayName("when compared, then they share the same hash code")
      void whenCompared_thenSameHashCode() {

        final var firstPath = ActorPath.root().append("actor").append("child");
        final var secondPath = ActorPath.root().append("actor").append("child");

        assertThat(firstPath.hashCode()).isEqualTo(secondPath.hashCode());
      }
    }

    @Nested
    @DisplayName("given two paths that differ only in the last segment")
    class GivenDifferentLastSegment {

      @Test
      @DisplayName("when compared, then they are not equal")
      void whenCompared_thenNotEqual() {

        final var firstPath = ActorPath.root().append("actor").append("first-child");
        final var secondPath = ActorPath.root().append("actor").append("second-child");

        assertThat(firstPath).isNotEqualTo(secondPath);
      }
    }

    @Nested
    @DisplayName("given two paths that share the same name but have different parents")
    class GivenSameNameDifferentParent {

      @Test
      @DisplayName("when compared, then they are not equal")
      void whenCompared_thenNotEqual() {

        final var firstPath = ActorPath.root().append("branch-a").append("leaf");
        final var secondPath = ActorPath.root().append("branch-b").append("leaf");

        assertThat(firstPath).isNotEqualTo(secondPath);
      }
    }

    @Nested
    @DisplayName("given a path compared to itself")
    class GivenSameInstance {

      @Test
      @DisplayName("when compared to itself, then equals returns true")
      void whenComparedToItself_thenEqual() {

        final var path = ActorPath.root().append("actor");

        //noinspection EqualsWithItself
        assertThat(path).isEqualTo(path);
      }
    }

    @Nested
    @DisplayName("given a path compared to null or a different type")
    class GivenNullOrDifferentType {

      @Test
      @DisplayName("when compared to null, then equals returns false")
      void whenComparedToNull_thenNotEqual() {
        assertThat(ActorPath.root().append("actor")).isNotEqualTo(null);
      }

      @Test
      @DisplayName("when compared to a String, then equals returns false")
      void whenComparedToString_thenNotEqual() {
        assertThat(ActorPath.root().append("actor")).isNotEqualTo("/actor");
      }
    }

    @Nested
    @DisplayName("given two root paths")
    class GivenRootPaths {

      @Test
      @DisplayName("when compared, then they are equal")
      void whenCompared_thenEqual() {

        final var firstRoot = ActorPath.root();
        final var secondRoot = ActorPath.root();

        assertThat(firstRoot).isEqualTo(secondRoot);
      }

      @Test
      @DisplayName("when compared, then they share the same hash code")
      void whenCompared_thenSameHashCode() {

        final var firstRoot = ActorPath.root();
        final var secondRoot = ActorPath.root();

        assertThat(firstRoot.hashCode()).isEqualTo(secondRoot.hashCode());
      }
    }
  }

  @Nested
  @DisplayName("when used as a ConcurrentHashMap key")
  class ConcurrentHashMapKey {

    @Test
    @DisplayName("given two distinct paths, when both are inserted, then the map contains two entries")
    void givenTwoPaths_whenBothInserted_thenMapHasTwoEntries() {

      final var firstPath = ActorPath.root().append("actor").append("first-child");
      final var secondPath = ActorPath.root().append("actor").append("second-child");
      final var map = new ConcurrentHashMap<ActorPath, String>();

      map.put(firstPath, "first");
      map.put(secondPath, "second");

      assertThat(map).hasSize(2);
    }

    @Test
    @DisplayName("given two distinct paths, when the first is removed, then only the second remains")
    void givenTwoPaths_whenFirstRemoved_thenSecondRemains() {

      final var firstPath = ActorPath.root().append("actor").append("first-child");
      final var secondPath = ActorPath.root().append("actor").append("second-child");

      final var map = new ConcurrentHashMap<ActorPath, String>();
      map.put(firstPath, "first");
      map.put(secondPath, "second");

      map.remove(firstPath);

      assertThat(map).isNotEmpty();
      assertThat(map).containsKey(secondPath);
      assertThat(map).doesNotContainKey(firstPath);
    }

    @Test
    @DisplayName("given two distinct paths, when both are removed, then the map is empty")
    void givenTwoPaths_whenBothRemoved_thenMapIsEmpty() {

      final var firstPath = ActorPath.root().append("actor").append("first-child");
      final var secondPath = ActorPath.root().append("actor").append("second-child");

      final var map = new ConcurrentHashMap<ActorPath, String>();
      map.put(firstPath, "first");
      map.put(secondPath, "second");

      map.remove(firstPath);
      map.remove(secondPath);

      // Then
      assertThat(map).isEmpty();
    }

    @Test
    @DisplayName("given a path inserted under one reference, when looked up with an equal but distinct instance, then the entry is found")
    void givenInsertedPath_whenLookedUpWithEqualInstance_thenEntryFound() {

      final var insertedPath = ActorPath.root().append("actor").append("child");
      final var equivalentPath = ActorPath.root().append("actor").append("child");

      final var map = new ConcurrentHashMap<ActorPath, String>();
      map.put(insertedPath, "value");

      final var result = map.get(equivalentPath);

      assertThat(result).isEqualTo("value");
    }
  }
}
