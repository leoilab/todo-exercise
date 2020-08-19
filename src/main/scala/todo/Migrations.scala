package todo

import doobie.implicits._
import zio._
import zio.interop.catz._

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

  def run: URIO[Transactional, Unit] = {
    ZIO.service[Trx].flatMap { transactor =>
      migrationV1.update.run.transact(transactor).unit.orDie
    }
  }

}
