package todo

import doobie.Transactor
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

object Main extends zio.App {

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val transactor = Transactor.fromDriverManager[Task](
      driver = "org.sqlite.JDBC",
      url    = "jdbc:sqlite:todo.db"
    )

    program.provideLayer(ZLayer.succeed(transactor))
  }

  val program: URIO[Transactional, ExitCode] = {
    for {
      _ <- Migrations.run
      transactor <- ZIO.service[Trx]
      _ <- Task.concurrentEffectWith { implicit ce =>
        Server.run.provide(Has(transactor))
      }
    } yield ExitCode.success
  }
}
