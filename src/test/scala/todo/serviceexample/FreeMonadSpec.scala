package todo.serviceexample

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.free.Free
import cats.~>
import doobie.free.connection.ConnectionIO
import org.scalatest._
import shapeless.{Inl, Inr}
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.FreeMonad
import todo.serviceexamples.FreeMonad.Implementation.{ConnectionIOInterpreter, StoreInterpreter, TrxInterpreter}
import todo.serviceexamples.FreeMonad._

class FreeMonadSpec extends FunSpec with DBTestHelper with Matchers {

  // You wouldn't normally want to do this because it would
  // test the implementation instead of the interface so it's
  // just here to show how to do whitebox mocking
  type Messages = Vector[(String, Option[Throwable])]
  final class FakeLogger(messageStore: Ref[IO, Messages]) extends (FreeMonad.LoggerOp ~> IO) {
    def apply[T](op: LoggerOp[T]): IO[T] = {
      op match {
        case LogInfo(message) =>
          messageStore.update(_ :+ (message, None))
        case LogError(message, cause) =>
          messageStore.update(_ :+ (message, Some(cause)))
      }
    }
  }

  def createInterpreter: IO[(Ref[IO, Messages], Dsl ~> IO)] = {
    for {
      messages <- Ref.of[IO, Messages](Vector.empty)
      logger           = new FakeLogger(messages)
      storeInterpreter = new StoreInterpreter
      trxInterpreter   = new TrxInterpreter(storeInterpreter)
      dbInterpreter_0  = trxInterpreter or storeInterpreter
      dbInterpreter_1  = new ConnectionIOInterpreter(DBTestHelper.transactor).compose(dbInterpreter_0)
      interpreter      = logger or dbInterpreter_1
    } yield (messages, interpreter)
  }

  class Executor(interpreter: Dsl ~> IO) {
    def apply[A](program: Free[Dsl, A]): IO[A] = program.foldMap(interpreter)
  }

  def iot[A](
      message: String
  )(body:      (Ref[IO, Messages], Implementation.Service, Executor) => IO[A]): Unit = {
    it(message) {
      createInterpreter
        .flatMap {
          case (messages, interpreter) =>
            body(messages, new Implementation.Service(), new Executor(interpreter))
        }
        .unsafeRunSync()
    }
  }

  describe("IO") {

    describe("finish") {

      iot("should fail without logs if the id is invalid ") {
        case (messages, service, execute) =>
          // FreeT might help with interleaving IO and Free steps but it might be too complicated
          for {
            (result, finalTodos) <- execute {
              for {
                result <- service.finish(-1)
                finalTodos <- service.list
              } yield (result, finalTodos)
            }
            finalMessages <- messages.get
          } yield {
            // Checking finalMessages is not a good idea in general, see the note at FakeLogger
            finalMessages should be(empty)
            finalTodos should be(empty)
            result should be('left)
            result.left.toOption.get should be(Inl(InvalidId(-1)))
          }
      }

      iot("should fail when the todo is missing") {
        case (_, service, execute) =>
          val program = for {
            result <- service.finish(1)
            finalTodos <- service.list
          } yield (result, finalTodos)

          execute(program).map {
            case (result, finalTodos) =>
              finalTodos should be(empty)
              result should be('left)
              // Note: if we go with error handling with coproducts we should
              // build a matcher that can extract the error in a nicer way (contain?)
              result.left.toOption.get should be(Inr(Inl(TodoNotFound(1))))
          }
      }

      iot("should work when the todo exists") {
        case (_, service, execute) =>
          val todoName = "test"
          val program = for {
            _ <- service.create(todoName)
            // .head is ok here because we just created a Todo
            todo <- service.list.map(_.head)
            result <- service.finish(todo.id)
            // .head is ok here because we just created a Todo
            finishedTodo <- service.list.map(_.head)
          } yield (todo, result, finishedTodo)

          execute(program).map {
            case (todo, result, finishedTodo) =>
              result should be('right)
              todo.done should be(false)
              finishedTodo.done should be(true)
              todo.id should be(finishedTodo.id)
          }
      }

    }

  }

}
