package todo

import cats.effect.IO
import cats.syntax.functor._
import doobie.implicits._
import doobie.Transactor

object Migrations {

  val migrationV1 = {
    sql"""
         |create table if not exists todo(
         | id integer primary key,
         | name text not null,
         | done tinyint not null default 0
         |)
         |""".stripMargin
  }

  def run(transactor: Transactor[IO]): IO[Unit] = {
    migrationV1.update.run.transact(transactor).void
  }

}
