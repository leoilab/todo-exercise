package todo

import cats.data.EitherT
import cats.{Apply, Monad, MonadError}
import cats.effect.IO
import shapeless.{:+:, CNil, Coproduct}
import todo.Model.Todo
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import doobie.implicits._
import doobie.free.connection.{ConnectionIO, ConnectionOp}
import doobie.util.transactor.Transactor
import todo.Errors.{DomainError, TodoNotFound}

import scala.concurrent.ExecutionContext

object Errors {
  sealed abstract class DomainError(message: String) extends Throwable(message)
  final case class TodoNotFound(id:          Int) extends DomainError(s"Todo not found: ${id}")
  final case class SomeOtherProblem()

  type Example = TodoNotFound :+: SomeOtherProblem :+: CNil
}

trait TodoServiceIO {
  def list: IO[Vector[Todo]]
  def create(name:         String): IO[Unit]
  def finish(id:           Int):    IO[Unit]
  def finishTypedError(id: Int):    IO[Either[Errors.TodoNotFound, Unit]]
  def complexErrorExample: IO[Either[Errors.Example, Unit]]
}

final class TodoServiceIOImpl(val todoRepository: TodoRepositoryIO, val logger: LoggerIO) extends TodoServiceIO {
  def list: IO[Vector[Todo]] = {
    todoRepository.list
  }

  def create(name: String): IO[Unit] = {
    logger.logInfo(s"Creating new todo: ${name}") *>
      todoRepository.create(name)
  }

  def finish(id: Int): IO[Unit] = {
    logger.logInfo(s"Finishing todo: ${id}") *>
      todoRepository.finish(id).flatMap {
        case UpdateResult.Updated  => IO.unit
        case UpdateResult.NotFound => IO.raiseError(Errors.TodoNotFound(id))
      }
  }

  def finishTypedError(id: Int): IO[Either[Errors.TodoNotFound, Unit]] = {
    logger.logInfo(s"Finishing todo: ${id}") *>
      todoRepository.finish(id).map {
        case UpdateResult.Updated  => Right(())
        case UpdateResult.NotFound => Left(Errors.TodoNotFound(id))
      }
  }

  def complexErrorExample: IO[Either[Errors.Example, Unit]] = {
    IO.pure {
      1 match {
        case 0 => Left(Coproduct[Errors.Example](Errors.TodoNotFound(1)))
        case 1 => Left(Coproduct[Errors.Example](Errors.SomeOtherProblem()))
        case _ => Right(Unit)
      }
    }
  }
}

object TodoServiceF {

  def list[F[_]: TodoRepositoryF]: F[Vector[Todo]] = {
    TodoRepositoryF[F].list
  }

  def create[F[_]: TodoRepositoryF: LoggerF: Monad](name: String): F[Unit] = {
    LoggerF[F].logInfo(s"Creating new todo: ${name}") *>
      TodoRepositoryF[F].create(name)
  }

// Untyped error
  def finish[F[_]: TodoRepositoryF: LoggerF: Monad](id: Int)(implicit error: MonadError[F, Throwable]): F[Unit] = {
    LoggerF[F].logInfo(s"Finishing todo: ${id}") *>
      TodoRepositoryF[F].finish(id).flatMap {
        case UpdateResult.Updated  => Monad[F].unit
        case UpdateResult.NotFound => error.raiseError(Errors.TodoNotFound(id))
      }
  }

  // Typed error
  def finishTypedError[F[_]: TodoRepositoryF: LoggerF: Monad](id: Int): F[Either[TodoNotFound, Unit]] = {
    LoggerF[F].logInfo(s"Finishing todo: ${id}") *>
      TodoRepositoryF[F].finish(id).flatMap {
        case UpdateResult.Updated  => Monad[F].pure(Right(()))
        case UpdateResult.NotFound => Monad[F].pure(Left(TodoNotFound(id)))
      }
  }

  def complexTypeError[F[_]: Monad]: F[Either[Errors.Example, Unit]] = {
    Monad[F].pure {
      1 match {
        case 0 => Left(Coproduct[Errors.Example](Errors.TodoNotFound(1)))
        case 1 => Left(Coproduct[Errors.Example](Errors.SomeOtherProblem()))
        case _ => Right(Unit)
      }
    }
  }

  def complexTypeError2[F[_]: Monad]: EitherT[F, Errors.Example, Unit] = {
    EitherT.fromEither {
      1 match {
        case 0 => Left(Coproduct[Errors.Example](Errors.TodoNotFound(1)))
        case 1 => Left(Coproduct[Errors.Example](Errors.SomeOtherProblem()))
        case _ => Right(Unit)
      }
    }
  }

}

object TaglessTrx {

  type Trx[A] = ConnectionIO[A]

  trait TodoStore[F[_]] {
    def list: F[Vector[Todo]]
    def create(name: String): Trx[Unit]
    def finish(id:   Int):    Trx[UpdateResult]
  }

  class TodoStoreImplForIO(transactor: Transactor[IO]) extends TodoStore[IO] {
    def list: IO[Vector[Todo]] = {
      sql"select id, name, done from todo".query[Todo].to[Vector].transact(transactor)
    }

    def create(name: String): Trx[Unit] = {
      val statement = sql"insert into todo (name, done) values (${name}, 0)"
      statement.update.run
        .flatMap {
          case 0       => MonadError[ConnectionIO, Throwable].raiseError(FailedToInsert(statement))
          case 1       => Monad[ConnectionIO].pure(())
          case results => MonadError[ConnectionIO, Throwable].raiseError(TooManyWriteResults(statement, results))
        }
    }

    def finish(id: Int): Trx[UpdateResult] = {
      MonadError[ConnectionIO, Throwable].raiseError[UpdateResult](new Exception("STOP"))
    }

    def finish_Working(id: Int): Trx[UpdateResult] = {
      val statement = sql"update todo set done = 1 where id = ${id}"
      statement.update.run.flatMap {
        case 0       => Monad[ConnectionIO].pure(UpdateResult.NotFound)
        case 1       => Monad[ConnectionIO].pure(UpdateResult.Updated)
        case results => MonadError[ConnectionIO, Throwable].raiseError(TooManyWriteResults(statement, results))
      }
    }
  }

  trait TrxHandler[F[_]] {
    def trx[A](action: Trx[A]): F[A]
  }

  class DoobieTrxHandler(transactor: Transactor[IO]) extends TrxHandler[IO] {
    def trx[A](action: Trx[A]): IO[A] = {
      action.transact(transactor)
    }
  }

  def program[F[_]: Monad](implicit store: TodoStore[F], trx: TrxHandler[F], logger: LoggerF[F]): F[Unit] = {
    for {
      _ <- logger.logInfo("Tagless transaction test")
      _ <- trx.trx {
        for {
          _ <- store.create("failed")
          _ <- store.finish(1)
        } yield ()
      }
      _ <- logger.logInfo("Success")
    } yield ()
  }

  def main(args: Array[String]): Unit = {
    implicit val cs = IO.contextShift(ExecutionContext.global)
    val transactor = Transactor.fromDriverManager[IO](
      "org.sqlite.JDBC",
      "jdbc:sqlite:todo.db"
    )
    implicit val logger = new LoggerFImplForIO
    // You can use a readonly connection for the store
    // or configure different thread pools for read/write transactions
    implicit val store      = new TodoStoreImplForIO(transactor)
    implicit val trxHandler = new DoobieTrxHandler(transactor)

    program[IO].unsafeRunSync()

    ()
  }

}
