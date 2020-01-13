package todo.serviceexamples

import cats.Monad
import cats.data.EitherT
import cats.effect.IO
import cats.syntax.apply._
import doobie.implicits._
import doobie.util.transactor.Transactor
import shapeless.Coproduct
import todo.{FailedToInsert, TooManyWriteResults}
import todo.serviceexamples.Common._

object Tagless {

  trait Logger[F[_]] {
    def info(message:  String): F[Unit]
    def error(message: String, cause: Throwable): F[Unit]
  }
  object Logger {
    @inline def apply[F[_]](implicit instance: Logger[F]): Logger[F] = instance
  }

  trait Store[F[_]] {
    def list: F[Vector[Todo]]
    def create(name: String): Trx[Unit]
    def finish(id:   Int):    Trx[UpdateResult]
  }
  object Store {
    @inline def apply[F[_]](implicit instance: Store[F]): Store[F] = instance
  }

  trait TrxHandler[F[_]] {
    def run[T](operation: Trx[T]): F[T]
  }
  object TrxHandler {
    @inline def apply[F[_]](implicit instance: TrxHandler[F]): TrxHandler[F] = instance
  }

  object Implementation {

    // Implementation is identical to the SimpleIO.Implementation.LoggerImpl
    object LoggerIO extends Logger[IO] {
      def info(message:  String): IO[Unit] = IO(println(s"[INFO] ${message}"))
      def error(message: String, cause: Throwable): IO[Unit] = IO(println(s"[ERROR] ${message} caused by: ${cause}"))
    }

    // Implementation is identical to the SimpleIO.Implementation.LoggerImpl
    final class StoreIO(transactor: Transactor[IO]) extends Store[IO] {
      def list: IO[Vector[Todo]] = {
        sql"select id, name, done from todo".query[Todo].to[Vector].transact(transactor)
      }

      def create(name: String): Trx[Unit] = {
        val statement = sql"insert into todo (name, done) values (${name}, 0)"

        statement.update.run
          .flatMap {
            case 0       => Trx.raiseError(FailedToInsert(statement))
            case 1       => Trx.unit
            case results => Trx.raiseError(TooManyWriteResults(statement, results))
          }
      }

      def finish(id: Int): Trx[UpdateResult] = {

        val statement = sql"update todo set done = 1 where id = ${id}"

        statement.update.run.flatMap {
          case 0       => Trx.pure(UpdateResult.NotFound)
          case 1       => Trx.pure(UpdateResult.Updated)
          case results => Trx.raiseError(TooManyWriteResults(statement, results))
        }
      }
    }

    // Implementation is identical to the SimpleIO.Implementation.LoggerImpl
    final class TrxHandlerIO(transactor: Transactor[IO]) extends TrxHandler[IO] {
      def run[T](operation: Trx[T]): IO[T] = operation.transact(transactor)
    }

    object Service {

      def list[F[_]: Store]: F[Vector[Todo]] = Store[F].list

      // Monad could be Apply because we only use *>
      def create[F[_]: Monad: Logger: Store: TrxHandler](name: String): F[Unit] = {
        Logger[F].info(s"Creating todo: '${name}'") *>
          TrxHandler[F].run(Store[F].create(name))
      }

      def finish[F[_]: Monad: Logger: Store: TrxHandler](id: Int): EitherT[F, FinishError, Unit] = {
        for {
          _ <- EitherT.fromEither[F](if (id < 0) Left(Coproduct[FinishError](InvalidId(id))) else Right(()))
          _ <- EitherT.liftF(Logger[F].info(s"Finishing todo: ${id}"))
          updateResult <- EitherT
            .liftF[F, FinishError, UpdateResult](TrxHandler[F].run(Store[F].finish(id)))
            .flatMap[FinishError, Unit] {
              case UpdateResult.Updated  => EitherT.fromEither(Right())
              case UpdateResult.NotFound => EitherT.fromEither(Left(Coproduct[FinishError](TodoNotFound(id))))
            }
        } yield updateResult
      }

      // Alternative syntax:
      def list_alt[F[_]](implicit store: Store[F]): F[Vector[Todo]] = store.list

      // Monad could be Apply because we only use *>
      def create_alt[F[_]: Monad](
          name:          String
      )(implicit logger: Logger[F], store: Store[F], trx: TrxHandler[F]): F[Unit] = {
        logger.info(s"Creating todo: '${name}'") *>
          trx.run(store.create(name))
      }

      def finish_alt[F[_]: Monad](
          id:            Int
      )(implicit logger: Logger[F], store: Store[F], trx: TrxHandler[F]): EitherT[F, FinishError, Unit] = {
        for {
          _ <- EitherT.fromEither[F](if (id < 0) Left(Coproduct[FinishError](InvalidId(id))) else Right(()))
          _ <- EitherT.liftF(logger.info(s"Finishing todo: ${id}"))
          updateResult <- EitherT
            .liftF[F, FinishError, UpdateResult](trx.run(store.finish(id)))
            .flatMap[FinishError, Unit] {
              case UpdateResult.Updated  => EitherT.fromEither(Right())
              case UpdateResult.NotFound => EitherT.fromEither(Left(Coproduct[FinishError](TodoNotFound(id))))
            }
        } yield updateResult
      }

    }

  }

  def main(args: Array[String]): Unit = {
    implicit val logger = Implementation.LoggerIO
    implicit val store  = new Implementation.StoreIO(transactor)
    implicit val trx    = new Implementation.TrxHandlerIO(transactor)

    println(Implementation.Service.finish[IO](-1).value.unsafeRunSync)
  }

}
