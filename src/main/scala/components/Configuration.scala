package components

case class Configuration(nodes: Set[String],
                         preferenceList: Set[String],
                         seed: String,
                         vectorClock: VectorClock[String])

