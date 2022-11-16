
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.8"

ThisBuild / scapegoatVersion := "1.3.9"

scapegoatReports := Seq("xml")

Scapegoat / scalacOptions += "-P:scapegoat:overrideLevels:all=Warning"

ThisBuild / scalaBinaryVersion := "2.12"


val akkaVersion = "2.7.0"
val akkaHttpVersion = "10.4.0"
val akkaProjectionVersion = "1.3.0"
val cassandraVersion = "1.1.0"
val leveldbVersion = "0.9"
val leveldbjniVersion = "1.8"


ThisBuild / libraryDependencies ++= Seq(
  // akka related dependencies
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,

  // akka logging related dependencies
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.11",

  // akka stream related dependencies
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  // akka http related dependencies
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,

  // akka persistence related dependencies
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,

  // akka persistence cassandra related dependencies
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,


  // akka persistence local levelDB stores
  "org.iq80.leveldb" % "leveldb" % leveldbVersion,
  "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbjniVersion,

  // Cluster related dependencies
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,

  // Akka projection related dependencies
  "com.lightbend.akka" %% "akka-projection-core" % akkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-eventsourced" % akkaProjectionVersion,
  "com.lightbend.akka" %% "akka-projection-cassandra" % akkaProjectionVersion,

  // test related dependencies
  "org.scalatest" %% "scalatest" % "3.2.12" % Test,

  // Scala CSV related dependencies
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",

  // Config related dependencies
  "com.github.andyglow" %% "typesafe-config-scala" % "2.0.0",

  // Database query/access library related dependencies
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "org.postgresql" % "postgresql" % "42.3.4",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
  "com.github.tminglei" %% "slick-pg" % "0.20.3",
  "com.github.tminglei" %% "slick-pg_play-json" % "0.20.3",

)

lazy val root = (project in file("."))
  .settings(
    name := "akka-restaurant-reviews"
  )
