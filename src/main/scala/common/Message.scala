package common

object Message {

    case class Put(key:String, value:String) {
        override def toString: String = s"Put($key, $value)"
    }

    case class Get(key:String) {
        override def toString: String = s"Get($key)"
    }

    case class Reply(msg:Any) {
        override def toString: String = s"Reply(${msg.toString})"
    }

}
