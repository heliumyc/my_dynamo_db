package environment

/**
 * WARS model, see PBS paper
 * @param writeDelay
 * @param ackDelay
 * @param readDelay
 * @param responseDelay
 */
case class FuzzParams(writeDelay: Double = 0, arsDelay: Double = 0, dropRate: Double = 0)
