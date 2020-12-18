package environment

/**
 * WARS model, see PBS paper
 * @param writeDelay
 * @param ackDelay
 * @param readDelay
 * @param responseDelay
 */
case class Delay(writeDelay: Double = 0, arsDelay: Double = 0)
