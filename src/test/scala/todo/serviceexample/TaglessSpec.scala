package todo.serviceexample

import cats.effect.IO
import cats.effect.concurrent.Ref
import org.scalatest._
import shapeless.{Inl, Inr}
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.Tagless
import todo.serviceexamples.Tagless.Implementation.Service

class TaglessSpec extends FunSpec with DBTestHelper with Matchers {

  // You wouldn't normally want to do this because it would
  // test the implementation instead of the interface so it's
  // just here to show how to do whitebox mocking
  type Messages = Vector[(String, Option[Throwable])]
  // Implementation is identical to SimpleIOSpec.FakeLogger
  final class FakeLogger(messageStore: Ref[IO, Messages]) extends Tagless.Logger[IO] {
    def info(message: String): IO[Unit] = {
      messageStore.update(_ :+ (message, None))
    }
    def error(message: String, cause: Throwable): IO[Unit] = {
      messageStore.update(_ :+ (message, Some(cause)))
    }
  }

  trait Dependencies {
    val messages:            Ref[IO, Messages]
    implicit val loggerImpl: Tagless.Logger[IO]
    implicit val storeImpl:  Tagless.Store[IO]
    implicit val trxImpl:    Tagless.TrxHandler[IO]
  }

  def createFakes: IO[Dependencies] = {
    for {
      ms <- Ref.of[IO, Messages](Vector.empty)
      logger = new FakeLogger(ms)
      store  = new Tagless.Implementation.StoreIO(DBTestHelper.transactor)
      trx    = new Tagless.Implementation.TrxHandlerIO(DBTestHelper.transactor)
    } yield
      new Dependencies {
        val messages            = ms
        implicit val loggerImpl = logger
        implicit val storeImpl  = store
        implicit val trxImpl    = trx
      }
  }

  // Alternative io test definition with fakes built in
  def iot[A](message: String)(body: Dependencies => IO[A]): Unit = {
    it(message) {
      createFakes.flatMap(body).unsafeRunSync()
    }
  }

  describe("IO") {

    describe("finish") {

      iot("should fail without logs if the id is invalid ") { deps =>
        import deps._

        for {
          result <- Service.finish[IO](-1).value
          finalMessages <- messages.get
          finalTodos <- Service.list
        } yield {
          // Checking finalMessages is not a good idea in general, see the note at FakeLogger
          finalMessages should be(empty)
          finalTodos should be(empty)
          result should be('left)
          result.left.toOption.get should be(Inl(InvalidId(-1)))
        }
      }

      iot("should fail when the todo is missing") { deps =>
        import deps._

        for {
          result <- Service.finish[IO](1).value
        } yield {
          result should be('left)
          // Note: if we go with error handling with coproducts we should
          // build a matcher that can extract the error in a nicer way (contain?)
          result.left.toOption.get should be(Inr(Inl(TodoNotFound(1))))
        }
      }

      iot("should work when the todo exists") { deps =>
        import deps._

        val todoName = "test"

        for {
          _ <- Service.create[IO](todoName)
          // .head is ok here because we just created a Todo
          todo <- Service.list.map(_.head)
          result <- Service.finish[IO](todo.id).value
          // .head is ok here because we just created a Todo
          finishedTodo <- Service.list.map(_.head)
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

/* Exception trace for a missing db when calling `create` in "should work when the todo exists":

[SQLITE_ERROR] SQL error or missing database (no such table: todo)
org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (no such table: todo)
	at org.sqlite.core.DB.newSQLException(DB.java:941)
	at org.sqlite.core.DB.newSQLException(DB.java:953)
	at org.sqlite.core.DB.throwex(DB.java:918)
	at org.sqlite.core.NativeDB.prepare_utf8(Native Method)
	at org.sqlite.core.NativeDB.prepare(NativeDB.java:134)
	at org.sqlite.core.DB.prepare(DB.java:257)
	at org.sqlite.core.CorePreparedStatement.<init>(CorePreparedStatement.java:47)
	at org.sqlite.jdbc3.JDBC3PreparedStatement.<init>(JDBC3PreparedStatement.java:30)
	at org.sqlite.jdbc4.JDBC4PreparedStatement.<init>(JDBC4PreparedStatement.java:19)
	at org.sqlite.jdbc4.JDBC4Connection.prepareStatement(JDBC4Connection.java:35)
	at org.sqlite.jdbc3.JDBC3Connection.prepareStatement(JDBC3Connection.java:241)
	at org.sqlite.jdbc3.JDBC3Connection.prepareStatement(JDBC3Connection.java:205)
	at doobie.free.KleisliInterpreter$ConnectionInterpreter.$anonfun$prepareStatement$1(kleisliinterpreter.scala:800)
	at doobie.free.KleisliInterpreter.$anonfun$primitive$2(kleisliinterpreter.scala:118)
	at cats.effect.internals.IORunLoop$.cats$effect$internals$IORunLoop$$loop(IORunLoop.scala:87)
	at cats.effect.internals.IORunLoop$.startCancelable(IORunLoop.scala:41)
	at cats.effect.internals.IOBracket$BracketStart.run(IOBracket.scala:86)
	at cats.effect.internals.Trampoline.cats$effect$internals$Trampoline$$immediateLoop(Trampoline.scala:70)
	at cats.effect.internals.Trampoline.startLoop(Trampoline.scala:36)
	at cats.effect.internals.TrampolineEC$JVMTrampoline.super$startLoop(TrampolineEC.scala:93)
	at cats.effect.internals.TrampolineEC$JVMTrampoline.$anonfun$startLoop$1(TrampolineEC.scala:93)
	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:23)
	at scala.concurrent.BlockContext$.withBlockContext(BlockContext.scala:85)
	at cats.effect.internals.TrampolineEC$JVMTrampoline.startLoop(TrampolineEC.scala:93)
	at cats.effect.internals.Trampoline.execute(Trampoline.scala:43)
	at cats.effect.internals.TrampolineEC.execute(TrampolineEC.scala:44)
	at cats.effect.internals.IOBracket$BracketStart.apply(IOBracket.scala:72)
	at cats.effect.internals.IOBracket$BracketStart.apply(IOBracket.scala:52)
	at cats.effect.internals.IORunLoop$.cats$effect$internals$IORunLoop$$loop(IORunLoop.scala:136)
	at cats.effect.internals.IORunLoop$RestartCallback.signal(IORunLoop.scala:355)
	at cats.effect.internals.IORunLoop$RestartCallback.apply(IORunLoop.scala:376)
	at cats.effect.internals.IORunLoop$RestartCallback.apply(IORunLoop.scala:316)
	at cats.effect.internals.IOShift$Tick.run(IOShift.scala:36)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
	at java.lang.Thread.run(Thread.java:748)
 */
