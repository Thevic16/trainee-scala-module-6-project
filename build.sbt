ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.8"

val akkaVersion = "2.5.20"
val akkaHttpVersion = "10.1.7"
val scalaTestVersion = "3.0.5"
val cassandraVersion = "0.91"
val leveldbVersion = "0.7"
val leveldbjniVersion = "1.8"

ThisBuild / libraryDependencies ++= Seq(
  // akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,

  // akka stream
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  // akka http
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,


  // akka persistence
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,

  // akka persistence cassandra
  "com.typesafe.akka" %% "akka-persistence-cassandra" % cassandraVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % cassandraVersion % Test,

  // akka persistence local levelDB stores
  "org.iq80.leveldb" % "leveldb" % leveldbVersion,
  "org.fusesource.leveldbjni" % "leveldbjni-all" % leveldbjniVersion,

  "org.scalatest" %% "scalatest" % "3.2.12" % Test,

  // Scala CSV
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",

  // Config
  "com.github.andyglow" %% "typesafe-config-scala" % "2.0.0"
)

lazy val root = (project in file("."))
  .settings(
    name := "akka-restaurant-reviews"
  )
