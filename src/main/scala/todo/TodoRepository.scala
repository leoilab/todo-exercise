package todo

import cats.MonadError
import cats.effect.{Bracket, IO}
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import todo.Model.Todo

sealed trait UpdateResult
object UpdateResult {
  case object Updated extends UpdateResult
  case object NotFound extends UpdateResult
}

sealed abstract class RepositoryError(message:  String) extends Throwable(message)
final case class FailedToInsert(statement:      Fragment) extends RepositoryError(s"Failed to insert: ${statement}")
final case class TooManyWriteResults(statement: Fragment, affectedRows: Int)
    extends RepositoryError(s"Too many write results(${affectedRows}) for: `${statement}`")

trait TodoRepositoryIO {
  def list: IO[Vector[Todo]]
  def create(name: String): IO[Unit]
  def finish(id:   Int):    IO[UpdateResult]
}

class TodoRepositoryIOImpl(transactor: Transactor[IO]) extends TodoRepositoryIO {
  def list: IO[Vector[Todo]] = {
    sql"select id, name, done from todo".query[Todo].to[Vector].transact(transactor)
  }

  def create(name: String): IO[Unit] = {
    val statement = sql"insert into todo (name, done) values (${name}, 0)"

    statement.update.run
      .transact(transactor)
      .flatMap {
        case 0       => IO.raiseError(FailedToInsert(statement))
        case 1       => IO.unit
        case results => IO.raiseError(TooManyWriteResults(statement, results))
      }
  }

  def finish(id: Int): IO[UpdateResult] = {
    val statement = sql"update todo set done = 1 where id = ${id}"

    statement.update.run.transact(transactor).flatMap {
      case 0       => IO.pure(UpdateResult.NotFound)
      case 1       => IO.pure(UpdateResult.Updated)
      case results => IO.raiseError(TooManyWriteResults(statement, results))
    }
  }
}

/***********************
  *  Tagless version   *
  **********************/
trait TodoRepositoryF[F[_]] {
  def list: F[Vector[Todo]]
  def create(name: String): F[Unit]
  def finish(id:   Int):    F[UpdateResult]
}
object TodoRepositoryF {
  type TodoRepositoryFIO = TodoRepositoryF[IO]
  @inline def apply[F[_]](implicit instance: TodoRepositoryF[F]): TodoRepositoryF[F] = instance
}

// Implementation is the same as TodoRepositoryIOImpl
class TodoRepositoryFImplIO(transactor: Transactor[IO])
    extends TodoRepositoryIOImpl(transactor)
    with TodoRepositoryF[IO]
