package todo.serviceexamples

import cats.effect.{IO, Sync}
import doobie.free.connection.ConnectionIO
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import shapeless.{:+:, CNil}

import scala.concurrent.ExecutionContext

object Common {

  implicit lazy val cs = IO.contextShift(ExecutionContext.global)
  lazy val transactor = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    "jdbc:sqlite:todo.db"
  )

  final case class Todo(
      id:   Int,
      name: String,
      done: Boolean
  )

  type Trx[T] = ConnectionIO[T]

  object Trx {
    def unit: Trx[Unit] = Sync[ConnectionIO].unit
    def pure[T](value:       T):         Trx[T] = Sync[ConnectionIO].pure(value)
    def raiseError[T](error: Throwable): Trx[T] = Sync[ConnectionIO].raiseError(error)
  }

  sealed abstract class DomainError(message: String) extends Throwable(message)
  final case class InvalidId(id:             Int) extends DomainError(s"Invalid id: ${id}")
  final case class TodoNotFound(id:          Int) extends DomainError(s"Todo with id: ${id} not found")

  type FinishError = InvalidId :+: TodoNotFound :+: CNil

  sealed trait UpdateResult
  object UpdateResult {
    case object Updated extends UpdateResult
    case object NotFound extends UpdateResult
  }

  sealed abstract class RepositoryError(message:  String) extends Throwable(message)
  final case class FailedToInsert(statement:      Fragment) extends RepositoryError(s"Failed to insert: ${statement}")
  final case class TooManyWriteResults(statement: Fragment, affectedRows: Int)
      extends RepositoryError(s"Too many write results(${affectedRows}) for: `${statement}`")
}
