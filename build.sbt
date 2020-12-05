name := "distributed_sys_dynamodb"

version := "0.1"
scalaVersion := "2.12.7"

val akkaVersion = "2.6.10"
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies +=
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
