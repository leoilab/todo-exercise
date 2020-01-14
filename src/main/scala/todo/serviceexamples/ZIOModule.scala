package todo.serviceexamples

import doobie.implicits._
import doobie.util.transactor.Transactor
import shapeless.Coproduct
import todo.serviceexamples.Common._
import zio.interop.catz._
import zio.{DefaultRuntime, IO, RIO, Task, UIO, ZIO}

// Not sure what's the point of the environment when using this module pattern
// Reminds me a bit to the cake pattern, accidentally overwriting dependencies can be a problem
// Idea: try Has* pattern with environment for injection
object ZIOModule {

  trait Logger {
    val logger: Logger.Service[Any]
  }
  object Logger {
    trait Service[R] {
      def info(message:  String): RIO[R, Unit]
      def error(message: String, cause: Throwable): RIO[R, Unit]
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
  }

  trait TrxHandler {
    val trx: TrxHandler.Service[Any]
  }
  object TrxHandler {
    trait Service[R] {
      def run[T](operation: Trx[T]): RIO[R, T]
    }
  }

  trait TodoService {
    val todoService: TodoService.Service[Any]
  }
  object TodoService {
    trait Service[R] {
      def list: RIO[R, Vector[Todo]]
      def create(name: String): RIO[R, Unit]
      def finish(id:   Int):    ZIO[R, FinishError, Unit]
    }
  }

  object Implementation {

    trait LoggerImpl extends Logger {
      final val logger = new Logger.Service[Any] {
        def info(message: String): Task[Unit] = {
          // Works without .effect as well:
          // RIO(println(s"[INFO] ${message}"))
          RIO.effect(println(s"[INFO] ${message}"))
        }

        def error(message: String, cause: Throwable): Task[Unit] = {
          RIO.effect(println(s"[ERROR] ${message} caused by: ${cause}"))
        }
      }
    }

    trait StoreImpl extends Store {

      // dependencies:
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

    trait TodoServiceImpl extends TodoService {

      // dependencies:
      val logger: Logger.Service[Any]
      val store:  Store.Service[Any]
      val trx:    TrxHandler.Service[Any]

      final val todoService = new TodoService.Service[Any] {
        def list: Task[Vector[Todo]] = store.list

        def create(name: String): Task[Unit] = {
          for {
            _ <- logger.info(s"Creating todo: '${name}'")
            _ <- trx.run(store.create(name))
          } yield ()
        }

        // Error mapping seems to be the simplest but still needs a few .refineToOrDie to lift RIO/Task operations
        def finish(id: Int): IO[FinishError, Unit] = {
          for {
            _ <- if (id < 0) ZIO.fail(Coproduct[FinishError](InvalidId(id))) else ZIO.succeed(())
            _ <- logger.info(s"Finishing todo: ${id}").refineToOrDie[FinishError]
            _ <- trx.run(store.finish(id)).refineToOrDie[FinishError].flatMap {
              case UpdateResult.Updated  => ZIO.succeed(())
              case UpdateResult.NotFound => ZIO.fail(Coproduct[FinishError](TodoNotFound(id)))
            }
          } yield ()
        }
      }

    }

  }

  class TestService
      extends Implementation.TodoServiceImpl
      with Implementation.LoggerImpl
      with Implementation.StoreImpl
      with Implementation.TrxHandlerImpl {
    val transactor = Transactor.fromDriverManager[Task](
      "org.sqlite.JDBC",
      "jdbc:sqlite:todo.db"
    )
  }

  def main(args: Array[String]): Unit = {
    val runtime = new DefaultRuntime {}

    runtime.unsafeRun(new TestService().todoService.finish(-1))
  }

}
