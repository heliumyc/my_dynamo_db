package components

import myutils.VectorClock

case class Configuration(nodes: Set[String],
                         preferenceList: Set[String],
                         seed: String,
                         vectorClock: VectorClock[String])
