package kvstore

import environment.{EmulatedActor, Logged}

case class Server() extends EmulatedActor with Logged {
    import Server._
    case class Record(value: Int, vectorClock: VectorClock[String])

    private var data = Map[Int, Record]()
    private var vectorClock = VectorClock[String]()

    override def receiveMsg: Receive = process()

    def process(): Receive = {
        case Put(key, value) =>
            vectorClock.increase(self.path.name)
            data += (key -> Record(value, vectorClock))
            sender().tell(OK(), context.parent)
        case Get(key) =>
            val res = data.get(key)
            val resValue: Option[Int] = res match {
                case Some(Record(value, _)) => Option(value)
                case _ => None
            }
            sender().tell(Result(key, resValue), context.parent)
    }
}

object Server {
    case class Put(key: Int, value: Int)
    case class Get(key: Int)
    case class Result(key: Int, value: Option[Int])
    case class OK()
}