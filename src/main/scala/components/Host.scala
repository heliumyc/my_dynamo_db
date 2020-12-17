package components

/**
 * refer to a physical host
 * @param address address such as ip or actor name in akka to find ActorRef
 */
case class Host(address: String)
