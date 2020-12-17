package components

import akka.actor.ActorRef

class ClientConnectionPool {

    type ClientAddress = ActorRef
    type Id = Int

    private var queryId: Id = 0;

    private var connectionPool: Map[Id, ClientAddress] = Map()

    def popClient(connectionId: Id): Option[ClientAddress] = {
        val conn = connectionPool.get(connectionId)
        connectionPool -= connectionId
        conn
    }

    def addConnection()(implicit clientAddress: ClientAddress): Id = {
        println(clientAddress)
        queryId += 1
        connectionPool += (queryId -> clientAddress)
        queryId
    }
}

object ClientConnectionPool {
    def apply(): ClientConnectionPool = {
        new ClientConnectionPool
    }
}
