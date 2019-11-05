package todo

import cats.effect.{ExitCode, IO, IOApp}
import doobie.Transactor

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val transactor = Transactor.fromDriverManager[IO](
      "org.sqlite.JDBC",
      "jdbc:sqlite:todo.db"
    )

    for {
      _ <- Migrations.run(transactor)
      _ <- Server.run(transactor)
    } yield ExitCode.Success
  }
}
