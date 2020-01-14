package todo.serviceexample

import doobie.implicits._
import doobie.util.transactor.Transactor
import shapeless.{Inl, Inr}
import todo.Migrations
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.ZIOModule
import zio._
import zio.interop.catz._
import zio.test._
import zio.test.Assertion._
import zio.test.environment._

object Fakes {

  val transactor = Transactor.fromDriverManager[Task](
    "org.sqlite.JDBC",
    "jdbc:sqlite:test.db"
  )

  // You wouldn't normally want to do this because it would
  // test the implementation instead of the interface so it's
  // just here to show how to do whitebox mocking
  type Messages = Vector[(String, Option[Throwable])]
  trait FakeLogger extends ZIOModule.Logger {
    val messageStore: Ref[Messages]
    final val logger = new ZIOModule.Logger.Service[Any] {
      def info(message: String): RIO[Any, Unit] = {
        messageStore.update(_ :+ (message, None)).unit
      }
      def error(message: String, cause: Throwable): RIO[Any, Unit] = {
        messageStore.update(_ :+ (message, Some(cause))).unit
      }
    }
  }

  class TestService(val messageStore: Ref[Messages], val transactor: Transactor[Task])
      extends ZIOModule.Implementation.TodoServiceImpl
      with FakeLogger
      with ZIOModule.Implementation.StoreImpl
      with ZIOModule.Implementation.TrxHandlerImpl {}

  def create: Task[(Ref[Messages], ZIOModule.TodoService)] = {
    for {
      messages <- Ref.make[Messages](Vector.empty)
      _ <- Migrations.migrationV1.update.run.transact(transactor)
      _ <- sql"delete from todo".update.run.transact(transactor).unit
      service = new TestService(messages, transactor)
    } yield (messages, service)
  }

}

// Not sure how to do beforeEach/All with ZIO test
object ZIOModuleSpec
    extends DefaultRunnableSpec(
      suite("finish")(
        testM("should fail without logs if the id is invalid") {
          for {
            (messages, service) <- Fakes.create
            result <- service.todoService.finish(-1).either
            finalMessages <- messages.get
          } yield assert(result, isLeft(equalTo(Inl(InvalidId(-1))))) &&
            assert(finalMessages, isEmpty)
        },
        testM("should fail when the todo is missing") {
          for {
            (_, service) <- Fakes.create
            result <- service.todoService.finish(1).either
          } yield assert(result, isLeft(equalTo(Inr(Inl(TodoNotFound(1))))))
        },
        testM("should work when the todo exists") {
          val todoName = "test"
          for {
            (_, service) <- Fakes.create
            _ <- service.todoService.create(todoName)
            // .head is ok here because we just created a Todo
            todo <- service.todoService.list.map(_.head)
            result <- service.todoService.finish(todo.id).either
            // .head is ok here because we just created a Todo
            finishedTodo <- service.todoService.list.map(_.head)
          } yield assert(result, isRight(isUnit)) &&
            assert(todo.done, isFalse) &&
            assert(finishedTodo.done, isTrue) &&
            assert(todo.id, equalTo(finishedTodo.id))
        }
      ),
      List(TestAspect.sequential)
    )
