// SPDX-License-Identifier: MIT OR Apache-2.0

package de.dangoe.concurrent.slact.core.internal;

import de.dangoe.concurrent.slact.core.ActorPath;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

interface ActorRegistry {

  @NotNull Optional<ActorWrapper<?>> get(@NotNull ActorPath path);

  void register(@NotNull ActorWrapper<?> actor);

  void unregister(@NotNull ActorPath actor);
}
