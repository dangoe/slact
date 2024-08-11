package de.dangoe.concurrent.slact.testhelper;

import de.dangoe.concurrent.slact.ActorPath;

public record ReceivedMessage<M>(M message, ActorPath sender) {

}
