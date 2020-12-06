package components

import common.Message
import environment.{EmulatedActor, Logged}

import scala.collection.mutable

class PhysicalNode(val pid:Int) extends EmulatedActor with Logged {

    val storage: mutable.HashMap[String, String] = mutable.HashMap()

    override protected def receiveMsg: Receive = {
        case Message.Put(key,value) => storage.put(key, value)
        case Message.Get(key) =>
            val msg = storage.get(key) match {
                case Some(v) => Message.Reply(v)
                case None => Message.Reply("Not found")
            }
            sender() ! msg
    }
}
