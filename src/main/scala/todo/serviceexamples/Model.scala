package todo.serviceexamples

import cats.effect.Sync
import doobie.free.connection.ConnectionIO
import doobie.util.fragment.Fragment

object Model {

  final case class Todo(
      id:   Int,
      name: String,
      done: Boolean
  )

  sealed abstract class DomainError(message: String) extends Throwable(message)
  final case class InvalidId(id:             Int) extends DomainError(s"Invalid id: ${id}")
  final case class TodoNotFound(id:          Int) extends DomainError(s"Todo with id: ${id} not found")

  type Trx[T] = ConnectionIO[T]

  object Trx {
    def unit: Trx[Unit] = Sync[ConnectionIO].unit
    def pure[T](value:       T):         Trx[T] = Sync[ConnectionIO].pure(value)
    def raiseError[T](error: Throwable): Trx[T] = Sync[ConnectionIO].raiseError(error)
  }

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
