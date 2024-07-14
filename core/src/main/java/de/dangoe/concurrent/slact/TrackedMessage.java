package de.dangoe.concurrent.slact;

public record TrackedMessage<M>(
    ActorPath sender,
    M message
) {}
