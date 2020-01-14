package todo.serviceexample

import cats.effect.IO
import cats.effect.concurrent.Ref
import org.scalatest._
import shapeless.{Inl, Inr}
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.SimpleIO

class SimpleIOSpec extends FunSpec with DBTestHelper with Matchers {

  // You wouldn't normally want to do this because it would
  // test the implementation instead of the interface so it's
  // just here to show how to do whitebox mocking
  type Messages = Vector[(String, Option[Throwable])]
  final class FakeLogger(messageStore: Ref[IO, Messages]) extends SimpleIO.Logger {
    def info(message: String): IO[Unit] = {
      messageStore.update(_ :+ (message, None))
    }
    def error(message: String, cause: Throwable): IO[Unit] = {
      messageStore.update(_ :+ (message, Some(cause)))
    }
  }

  def createFakes: IO[(Ref[IO, Messages], SimpleIO.Service)] = {
    for {
      messages <- Ref.of[IO, Messages](Vector.empty)
      logger  = new FakeLogger(messages)
      store   = new SimpleIO.Implementation.StoreImpl(DBTestHelper.transactor)
      trx     = new SimpleIO.Implementation.TrxHandlerImpl(DBTestHelper.transactor)
      service = new SimpleIO.Implementation.ServiceImpl(logger, store, trx)
    } yield (messages, service)
  }

  def iot[A](message: String)(body: => IO[A]): Unit = {
    it(message) {
      body.unsafeRunSync()
    }
  }

  describe("IO") {

    describe("finish") {

      iot("should fail without logs if the id is invalid ") {
        for {
          (messages, service) <- createFakes
          result <- service.finish(-1).value
          finalMessages <- messages.get
          finalTodos <- service.list
        } yield {
          // Checking finalMessages is not a good idea in general, see the note at FakeLogger
          finalMessages should be(empty)
          finalTodos should be(empty)
          result should be('left)
          result.left.toOption.get should be(Inl(InvalidId(-1)))
        }
      }

      iot("should fail when the todo is missing") {
        for {
          (_, service) <- createFakes
          result <- service.finish(1).value
        } yield {
          result should be('left)
          // Note: if we go with error handling with coproducts we should
          // build a matcher that can extract the error in a nicer way (contain?)
          result.left.toOption.get should be(Inr(Inl(TodoNotFound(1))))
        }
      }

      iot("should work when the todo exists") {
        val todoName = "test"
        for {
          (_, service) <- createFakes
          _ <- service.create(todoName)
          // .head is ok here because we just created a Todo
          todo <- service.list.map(_.head)
          result <- service.finish(todo.id).value
          // .head is ok here because we just created a Todo
          finishedTodo <- service.list.map(_.head)
        } yield {
          result should be('right)
          todo.done should be(false)
          finishedTodo.done should be(true)
          todo.id should be(finishedTodo.id)
        }
      }

    }

  }

}
