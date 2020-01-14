lazy val catsVersion   = "2.0.0"
lazy val circeVersion  = "0.12.2"
lazy val http4sVersion = "0.20.11"
lazy val zioVersion    = "1.0.0-RC17"

name := "todo"
version := "0.1"
scalaVersion := "2.12.10"
resolvers += Resolver.sonatypeRepo("releases")
scalacOptions += "-Ypartial-unification"
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsVersion,
  "org.typelevel" %% "cats-free" % catsVersion,
  "org.tpolecat" %% "doobie-core" % "0.7.1",
  "org.xerial" % "sqlite-jdbc" % "3.28.0",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "rho-swagger" % "0.20.0-M1",
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC10",
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
  "dev.zio" %% "zio-test" % zioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
)
parallelExecution in Test := false
testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
