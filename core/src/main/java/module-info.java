module de.dangoe.concurrent.slact {
  exports de.dangoe.concurrent.slact.core;
  exports de.dangoe.concurrent.slact.core.exception;
  exports de.dangoe.concurrent.slact.core.patterns.actors;
  exports de.dangoe.concurrent.slact.core.logging;
  exports de.dangoe.concurrent.slact.core.logging.internal;
  requires org.slf4j;
  requires org.jetbrains.annotations;
  requires java.desktop;
}