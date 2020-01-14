package todo.serviceexamples

import cats.data.EitherK
import cats.effect.IO
import cats.{~>, InjectK}
import cats.free.Free
import cats.syntax.apply._
import doobie.implicits._
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import shapeless.Coproduct
import todo.serviceexamples.Common._

object FreeMonad {

  sealed trait LoggerOp[T]
  final case class LogInfo(message:  String) extends LoggerOp[Unit]
  final case class LogError(message: String, cause: Throwable) extends LoggerOp[Unit]

  class LoggerInject[F[_]](implicit inject: InjectK[LoggerOp, F]) {
    def info(message: String): Free[F, Unit] = {
      Free.inject[LoggerOp, F](LogInfo(message))
    }
    def info(message: String, cause: Throwable): Free[F, Unit] = {
      Free.inject[LoggerOp, F](LogError(message, cause))
    }
  }
  object LoggerInject {
    implicit def loggerInject[F[_]](implicit inject: InjectK[LoggerOp, F]): LoggerInject[F] = {
      new LoggerInject[F]
    }
  }

  sealed trait StoreOp[T]
  case object StoreList extends StoreOp[Vector[Todo]]
  final case class StoreCreate(name: String) extends StoreOp[Unit]
  final case class StoreFinish(id:   Int) extends StoreOp[UpdateResult]

  class StoreInject[F[_]](implicit inject: InjectK[StoreOp, F]) {
    def list: Free[F, Vector[Todo]] = {
      Free.inject[StoreOp, F](StoreList)
    }
    def create(name: String): Free[StoreOp, Unit] = {
      Free.liftF(StoreCreate(name))
    }
    def finish(id: Int): Free[StoreOp, UpdateResult] = {
      Free.liftF(StoreFinish(id))
    }
  }
  object StoreInject {
    implicit def storeInject[F[_]](implicit inject: InjectK[StoreOp, F]): StoreInject[F] = {
      new StoreInject[F]
    }
  }

  sealed trait TrxOp[DB[_], T]
  final case class WithTrx[DB[_], T](op: Free[DB, T]) extends TrxOp[DB, T]

  class TrxInject[F[_], DB[_]](implicit inject: InjectK[TrxOp[DB, ?], F]) {
    def run[T](op: Free[DB, T]): Free[F, T] = {
      Free.inject[TrxOp[DB, ?], F](WithTrx[DB, T](op))
    }
  }
  object TrxInject {
    implicit def trxInject[F[_], DB[_]](implicit inject: InjectK[TrxOp[DB, ?], F]): TrxInject[F, DB] = {
      new TrxInject[F, DB]
    }
  }

  type Dsl_1[T] = EitherK[TrxOp[StoreOp, ?], StoreOp, T]
  type Dsl[T]   = EitherK[LoggerOp, Dsl_1, T]

  // Drawback for the implementation:
  // IntelliJ reports an error for every case because it doesn't understand GADTs very well
  // https://en.wikipedia.org/wiki/Generalized_algebraic_data_type
  object Implementation {

    object LoggerInterpreter extends (LoggerOp ~> IO) {
      def apply[T](op: LoggerOp[T]): IO[T] = {
        op match {
          case LogInfo(message)         => IO(println(s"[INFO] ${message}"))
          case LogError(message, cause) => IO(println(s"[ERROR] ${message} caused by: ${cause}"))
        }
      }
    }

    class StoreInterpreter extends (StoreOp ~> ConnectionIO) {
      def apply[T](op: StoreOp[T]): ConnectionIO[T] = {
        op match {
          case StoreList =>
            sql"select id, name, done from todo".query[Todo].to[Vector]
          case StoreCreate(name) =>
            val statement = sql"insert into todo (name, done) values (${name}, 0)"

            statement.update.run.flatMap {
              case 0       => Common.Trx.raiseError(FailedToInsert(statement))
              case 1       => Common.Trx.unit
              case results => Common.Trx.raiseError(TooManyWriteResults(statement, results))
            }
          case StoreFinish(id) =>
            val statement = sql"update todo set done = 1 where id = ${id}"

            statement.update.run.flatMap {
              case 0       => Common.Trx.pure(UpdateResult.NotFound)
              case 1       => Common.Trx.pure(UpdateResult.Updated)
              case results => Common.Trx.raiseError(TooManyWriteResults(statement, results))
            }
        }

      }
    }

    class TrxInterpreter[F[_]](repoInterpreter: F ~> ConnectionIO) extends (TrxOp[F, ?] ~> ConnectionIO) {
      def apply[T](op: TrxOp[F, T]): ConnectionIO[T] = {
        op match {
          case WithTrx(transactionalOp) =>
            transactionalOp.foldMap[ConnectionIO](repoInterpreter)
        }
      }
    }

    class ConnectionIOInterpreter(transactor: Transactor[IO]) extends (ConnectionIO ~> IO) {
      def apply[T](op: ConnectionIO[T]): IO[T] = {
        op.transact(transactor)
      }
    }

    class Service(
        implicit logger: LoggerInject[Dsl],
        store:           StoreInject[Dsl],
        trx:             TrxInject[Dsl, StoreOp]
    ) {

      def list: Free[Dsl, Vector[Todo]] = store.list

      def create(name: String): Free[Dsl, Unit] = {
        logger.info(s"Creating todo: '${name}'") *>
          trx.run(store.create(name))
      }

      // Can't chain errors, have to nest them.
      // FreeT or EitherT[Free, ?, ?] might solve it but it seems complicated
      def finish(id: Int): Free[Dsl, Either[FinishError, Unit]] = {
        if (id < 0) {
          Free.pure[Dsl, Either[FinishError, Unit]](Left(Coproduct[FinishError](InvalidId(id))))
        } else {
          logger.info(s"Finishing todo: ${id}") *>
            trx.run(store.finish(id)).map {
              case UpdateResult.NotFound => Left(Coproduct[FinishError](TodoNotFound(id)))
              case UpdateResult.Updated  => Right(())
            }
        }
      }

    }

    def createInterpreter(transactor: Transactor[IO]): Dsl ~> IO = {
      val storeInterpreter: StoreOp ~> ConnectionIO           = new StoreInterpreter
      val trxInterpreter:   TrxOp[StoreOp, ?] ~> ConnectionIO = new TrxInterpreter(storeInterpreter)
      val dbInterpreter_0:  Dsl_1 ~> ConnectionIO             = trxInterpreter or storeInterpreter
      val dbInterpreter_1:  Dsl_1 ~> IO                       = new ConnectionIOInterpreter(transactor).compose(dbInterpreter_0)

      LoggerInterpreter or dbInterpreter_1
    }

  }

  def main(args: Array[String]): Unit = {
    val interpreter = Implementation.createInterpreter(transactor)
    val service     = new Implementation.Service()

    println(service.finish(-1).foldMap(interpreter).unsafeRunSync)
  }

}
