/**
 * slact core actor system runtime.
 */
module de.dangoe.concurrent.slact {
  exports de.dangoe.concurrent.slact.core;
  exports de.dangoe.concurrent.slact.core.exception;
  exports de.dangoe.concurrent.slact.core.patterns.actors;
  exports de.dangoe.concurrent.slact.core.logging;
  exports de.dangoe.concurrent.slact.core.logging.internal;
  exports de.dangoe.concurrent.slact.core.util.concurrent;
  requires org.slf4j;
  requires org.jetbrains.annotations;
  requires java.desktop;
}