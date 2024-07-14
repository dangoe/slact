package de.dangoe.concurrent.slact;

public record TraceableMessage<M>(ActorPath sender, M message) {
}
