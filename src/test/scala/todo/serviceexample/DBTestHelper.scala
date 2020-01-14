package todo.serviceexample

import cats.effect.IO
import cats.syntax.functor._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, TestSuite}
import todo.Migrations
import zio.Task
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object DBTestHelper {
  implicit lazy val cs = IO.contextShift(ExecutionContext.global)
  lazy val transactor = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    "jdbc:sqlite:test.db"
  )
  lazy val zioTransactor = Transactor.fromDriverManager[Task](
    "org.sqlite.JDBC",
    "jdbc:sqlite:test.db"
  )
}

trait DBTestHelper extends BeforeAndAfterEach with BeforeAndAfterAll { self: TestSuite =>

  override def beforeAll(): Unit = {
    Migrations.run(DBTestHelper.transactor).unsafeRunSync()
  }

  override def beforeEach(): Unit = {
    sql"delete from todo".update.run.transact(DBTestHelper.transactor).void.unsafeRunSync
  }

}
