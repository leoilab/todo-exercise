package todo


import cats.effect.{ExitCode, IO, IOApp}
import doobie.Transactor
import doobie.util.transactor.Transactor.Aux

object Main extends IOApp {
  def inmemoryTransactor(): Aux[IO, Unit] =
    Transactor.fromDriverManager[IO](
      "org.sqlite.JDBC",
      "jdbc:sqlite::memory:"
    )

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
