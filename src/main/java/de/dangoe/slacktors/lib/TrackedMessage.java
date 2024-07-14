package de.dangoe.slacktors.lib;

public record TrackedMessage<M>(
    ActorPath sender,
    M message
) {}
