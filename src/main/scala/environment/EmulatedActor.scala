package environment

@Deprecated
abstract class EmulatedActor extends ActorExtension {

    protected def receiveMsg: Receive

    def receive: Receive = receiveExtension orElse receiveMsg

}
