lazy val catsVersion   = "2.0.0"
lazy val circeVersion  = "0.12.2"
lazy val http4sVersion = "0.21.7"
lazy val tapirVersion  = "0.16.15"

name := "todo"
version := "0.1"
scalaVersion := "2.13.2"
resolvers += Resolver.sonatypeRepo("releases")
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-interop-cats" % "2.1.4.0",
  "dev.zio" %% "zio" % "1.0.0",
  "org.tpolecat" %% "doobie-core" % "0.8.8",
  "org.xerial" % "sqlite-jdbc" % "3.28.0",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.scalatest" %% "scalatest" % "3.0.8" % Test
)
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
