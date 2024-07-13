package de.dangoe.slacktors.lib;

public record RoutedMessage<M>(
    ActorPath sender,
    ActorPath recipient,
    M message
) {}
