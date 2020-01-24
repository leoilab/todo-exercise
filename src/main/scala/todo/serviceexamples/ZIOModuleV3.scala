package todo.serviceexamples

import doobie.implicits._
import doobie.util.transactor.Transactor
import shapeless.Coproduct
import todo.serviceexamples.Common._
import zio._
import zio.interop.catz._

object ZIOModuleV3 {

  trait Logger {
    val logger: Logger.Service[Any]
  }
  object Logger {
    trait Service[R] {
      def info(message:  String): RIO[R, Unit]
      def error(message: String, cause: Throwable): RIO[R, Unit]
    }

    object > extends Logger.Service[Logger] {
      def info(message:  String) = ZIO.accessM(_.logger.info(message))
      def error(message: String, cause: Throwable) = ZIO.accessM(_.logger.error(message, cause))
    }
  }

  trait Store {
    val store: Store.Service[Any]
  }
  object Store {
    trait Service[R] {
      def list: RIO[R, Vector[Todo]]
      def create(name: String): Trx[Unit]
      def finish(id:   Int):    Trx[UpdateResult]
    }

    object > {
      def list: RIO[Any with Store, Vector[Todo]] = ZIO.accessM(_.store.list)
    }
  }

  trait TrxHandler {
    val trx: TrxHandler.Service[Any]
  }
  object TrxHandler {
    trait Service[R] {
      def run[T](operation: Trx[T]): RIO[R, T]
    }

    object > extends TrxHandler.Service[TrxHandler] {
      def run[T](operation: Trx[T]) = ZIO.accessM(_.trx.run(operation))
    }
  }

  trait TodoService {
    val todoService: TodoService.Service[Any]
  }
  object TodoService {
    trait Service[R] {
      def list: RIO[R with Store, Vector[Todo]]
      def create(name: String): RIO[R with Store with Logger with TrxHandler, Unit]
      def finish(id:   Int):    ZIO[R with Store with Logger with TrxHandler, FinishError, Unit]
    }

    object > extends TodoService.Service[TodoService] {
      def list = ZIO.accessM(_.todoService.list)
      def create(name: String) = ZIO.accessM(_.todoService.create(name))
      def finish(id:   Int)    = ZIO.accessM(_.todoService.finish(id))
    }
  }

  object Implementation {

    trait LoggerImpl extends Logger {
      final val logger = new Logger.Service[Any] {
        def info(message: String): Task[Unit] = {
          RIO.effect(println(s"[INFO] ${message}"))
        }

        def error(message: String, cause: Throwable): Task[Unit] = {
          RIO.effect(println(s"[ERROR] ${message} caused by: ${cause}"))
        }
      }
    }

    trait StoreImpl extends Store {

      val transactor: Transactor[Task]

      final val store = new Store.Service[Any] {
        def list: Task[Vector[Todo]] = {
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

    }

    trait TrxHandlerImpl extends TrxHandler {

      // dependencies:
      val transactor: Transactor[Task]

      final val trx = new TrxHandler.Service[Any] {
        def run[T](operation: Trx[T]): Task[T] = operation.transact(transactor)
      }

    }

    object Tx {
      def in[T](op: Store.Service[Any] => Trx[T]): RIO[Store with TrxHandler, T] = {
        ZIO.accessM[Store with TrxHandler] { env =>
          env.trx.run(op(env.store))
        }
      }
    }

    trait TodoServiceImpl extends TodoService {

      final val todoService = new TodoService.Service[Any] {
        def list = {
          Store.>.list
//          or
//          ZIO.accessM(_.store.list)
        }

        def create(name: String) = {
          for {
            _ <- Logger.>.info(s"Creating todo: '${name}'")
            _ <- ZIO.accessM[Store with TrxHandler](env => TrxHandler.>.run(env.store.create(name)))
          } yield ()
        }

        def finish(id: Int) = {
          for {
            _ <- if (id < 0) ZIO.fail(Coproduct[FinishError](InvalidId(id))) else ZIO.succeed(())
            _ <- Logger.>.info(s"Finishing todo: ${id}").refineToOrDie[FinishError]
            _ <- Tx
              .in(_.finish(id))
              .refineToOrDie[FinishError]
              .flatMap {
                case UpdateResult.Updated  => ZIO.succeed(())
                case UpdateResult.NotFound => ZIO.fail(Coproduct[FinishError](TodoNotFound(id)))
              }
          } yield ()
        }
      }

    }

  }

  trait TransactorProvider {
    val transactor = Transactor.fromDriverManager[Task](
      "org.sqlite.JDBC",
      "jdbc:sqlite:todo.db"
    )
  }

  def main(args: Array[String]): Unit = {
    val runtime = new DefaultRuntime {}
    val deps = new Implementation.TodoServiceImpl with Implementation.LoggerImpl with Implementation.StoreImpl
    with Implementation.TrxHandlerImpl with TransactorProvider {}
    val program = TodoService.>.finish(-1).provide(deps)

    println(
      runtime
        .unsafeRun(program)
    )
  }

}
