package todo

import cats.data.EitherT
import cats.{Apply, Monad, MonadError}
import cats.effect.IO
import shapeless.{:+:, CNil, Coproduct}
import todo.Model.Todo
import cats.syntax.apply._
import cats.syntax.flatMap._
import todo.Errors.{DomainError, TodoNotFound}

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
