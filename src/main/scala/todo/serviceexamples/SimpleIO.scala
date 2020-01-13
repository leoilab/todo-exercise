package todo.serviceexamples

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.apply._
import doobie.implicits._
import doobie.util.transactor.Transactor
import shapeless.Coproduct
import todo.{FailedToInsert, TooManyWriteResults}
import todo.serviceexamples.Common._

object SimpleIO {

  trait Logger {
    def info(message:  String): IO[Unit]
    def error(message: String, cause: Throwable): IO[Unit]
  }

  trait Store {
    def list: IO[Vector[Todo]]
    def create(name: String): Trx[Unit]
    def finish(id:   Int):    Trx[UpdateResult]
  }

  trait TrxHandler {
    def run[T](operation: Trx[T]): IO[T]
  }

  trait Service {
    // No explicit errors in any layer
    def list: IO[Vector[Todo]]
    // Explicit errors in Store propagated through Service
    def create(name: String): IO[Unit]
    // Explicit errors in Store, some of them are transformed
    // into domain errors in Service some are propagated
    def finish(id: Int): EitherT[IO, FinishError, Unit]
  }

  object Implementation {

    object LoggerImpl extends Logger {
      def info(message:  String): IO[Unit] = IO(println(s"[INFO] ${message}"))
      def error(message: String, cause: Throwable): IO[Unit] = IO(println(s"[ERROR] ${message} caused by: ${cause}"))
    }

    final class StoreImpl(transactor: Transactor[IO]) extends Store {
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

    final class TrxHandlerImpl(transactor: Transactor[IO]) extends TrxHandler {
      def run[T](operation: Trx[T]): IO[T] = operation.transact(transactor)
    }

    final class ServiceImpl(logger: Logger, store: Store, trx: TrxHandler) extends Service {
      def list: IO[Vector[Todo]] = store.list

      def create(name: String): IO[Unit] = {
        logger.info(s"Creating todo: '${name}'") *>
          trx.run(store.create(name))
      }

      def finish(id: Int): EitherT[IO, FinishError, Unit] = {
        for {
          _ <- EitherT.fromEither[IO](if (id < 0) Left(Coproduct[FinishError](InvalidId(id))) else Right(()))
          _ <- EitherT.liftF(logger.info(s"Finishing ${id}"))
          updateResult <- EitherT.liftF(trx.run(store.finish(id)))
          _ <- EitherT.fromEither[IO] {
            updateResult match {
              case UpdateResult.Updated  => Right(())
              case UpdateResult.NotFound => Left(Coproduct[FinishError](TodoNotFound(id)))
            }
          }
        } yield ()
      }
    }

  }

  def main(args: Array[String]): Unit = {
    val logger     = Implementation.LoggerImpl
    val store      = new Implementation.StoreImpl(transactor)
    val trxHandler = new Implementation.TrxHandlerImpl(transactor)
    val service    = new Implementation.ServiceImpl(logger, store, trxHandler)

    println(service.finish(-1).value.unsafeRunSync())
  }

}
