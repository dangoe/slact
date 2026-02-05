package de.dangoe.concurrent.slact.testsupport;

import de.dangoe.concurrent.slact.core.ActorPath;

public record ReceivedMessage<M>(M message, ActorPath sender) {

}
