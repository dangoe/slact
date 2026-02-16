package de.dangoe.concurrent.slact.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

public class ActorPathTest {

  @Test
  void foo() {

    final var firstPath = ActorPath.root().append("actor").append("first-child");
    final var secondPath = ActorPath.root().append("actor").append("second-child");

    assertThat(firstPath).isNotEqualTo(secondPath);

    final var map = new ConcurrentHashMap<ActorPath, String>();
    map.put(firstPath, "");
    map.put(secondPath, "");

    map.remove(firstPath);

    assertThat(map).isNotEmpty();

    map.remove(secondPath);

    assertThat(map).isEmpty();
  }
}
