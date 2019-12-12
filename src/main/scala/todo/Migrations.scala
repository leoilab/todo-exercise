package todo

import cats.effect.IO
import cats.syntax.functor._
import doobie.implicits._
import doobie.Transactor
import doobie.util.fragment

object Migrations {

  val migrationV1: fragment.Fragment = {
    sql"""
         |create table if not exists todo(
         | id integer primary key,
         | name text not null,
         | done tinyint not null default 0
         |)
         |""".stripMargin
  }

  val migrationV2: fragment.Fragment = {
    sql"""
         |create table if not exists users(
         | username text primary key,
         | passwordHashed text not null,
         | salt text not null
         |);
         |
         |alter table todo
         | add column username text not null default ''
         |  references users(username)
         |""".stripMargin
  }

  def run(transactor: Transactor[IO]): IO[Unit] = {
    migrationV1.update.run.transact(transactor).void
    migrationV2.update.run.transact(transactor).void
  }

}
