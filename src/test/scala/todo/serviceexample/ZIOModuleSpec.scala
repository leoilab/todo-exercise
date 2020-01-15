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

/* Exception trace for a missing db when calling `create` in "should work when the todo exists":

Note: it points to the correct line: at todo.serviceexamples.ZIOModule$Implementation$TodoServiceImpl$$anon$4.create(ZIOModule.scala:130)
ZIOModule.scala:130  _ <- trx.run(store.create(name))

Fiber failed.
    A checked error was not handled.
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
    	at zio.internal.FiberContext.evaluateNow(FiberContext.scala:458)
    	at zio.internal.FiberContext.$anonfun$evaluateLater$1(FiberContext.scala:661)
    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
    	at java.lang.Thread.run(Thread.java:748)

    Fiber:Id(1579085394533,0) was supposed to continue to:
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.ZIO.bracket_(ZIO.scala:147)
      a future continuation at cats.free.Free.foldMap(Free.scala:155)
      a future continuation at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      a future continuation at scala.Function1.andThen(Function1.scala:57)
      a future continuation at cats.free.Free.foldMap(Free.scala:155)
      a future continuation at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      a future continuation at cats.free.Free.foldMap(Free.scala:156)
      a future continuation at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.interop.CatsConcurrent.bracketCase(cats.scala:251)
      a future continuation at cats.free.Free.foldMap(Free.scala:155)
      a future continuation at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.interop.CatsConcurrent.bracketCase(cats.scala:251)
      a future continuation at cats.free.Free.foldMap(Free.scala:155)
      a future continuation at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.interop.CatsConcurrent.bracketCase(cats.scala:251)
      a future continuation at cats.free.Free.foldMap(Free.scala:155)
      a future continuation at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.interop.CatsConcurrent.bracketCase(cats.scala:251)
      a future continuation at todo.serviceexample.ZIOModuleSpec$$anonfun$$lessinit$greater$1.new(ZIOModuleSpec.scala:80)
      a future continuation at zio.test.package$.testM(package.scala:307)
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.ZIO.bracket_(ZIO.scala:147)
      a future continuation at zio.ZIO.run(ZIO.scala:1166)
      a future continuation at zio.ZManaged.use(ZManaged.scala:748)
      a future continuation at zio.test.Spec.foreachExec(Spec.scala:167)
      a future continuation at zio.ZIO.zipWith(ZIO.scala:1543)
      a future continuation at zio.test.Spec.foldM(Spec.scala:139)
      a future continuation at zio.ZIO.summarized(ZIO.scala:1274)
      a future continuation at zio.test.TestRunner.run(TestRunner.scala:42)
      a future continuation at zio.test.RunnableSpec.runSpec(RunnableSpec.scala:39)
      a future continuation at zio.test.RunnableSpec.main(RunnableSpec.scala:52)

    Fiber:Id(1579085394533,0) execution trace:
      at doobie.free.KleisliInterpreter.primitive(kleisliinterpreter.scala:118)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.internal.FiberContext.lock(FiberContext.scala:606)
      at zio.internal.FiberContext.lock(FiberContext.scala:606)
      at scala.Function1.andThen(Function1.scala:57)
      at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      at scala.Function1.andThen(Function1.scala:57)
      at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      at cats.free.Free.foldMap(Free.scala:156)
      at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      at cats.free.Free.foldMap(Free.scala:155)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.ZIOFunctions.bracketExit(ZIO.scala:1765)
      at zio.internal.FiberContext.unlock(FiberContext.scala:609)
      at zio.internal.FiberContext.unlock(FiberContext.scala:609)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.ZIO.run(ZIO.scala:1166)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at doobie.free.KleisliInterpreter.primitive(kleisliinterpreter.scala:118)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.internal.FiberContext.lock(FiberContext.scala:606)
      at zio.internal.FiberContext.lock(FiberContext.scala:606)
      at scala.Function1.andThen(Function1.scala:57)
      at cats.StackSafeMonad.tailRecM(StackSafeMonad.scala:15)
      at cats.effect.Resource.use(Resource.scala:118)
      at cats.effect.Resource$.apply(Resource.scala:258)
      at cats.effect.Resource$.make(Resource.scala:295)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.ZIOFunctions.bracketExit(ZIO.scala:1765)
      at zio.internal.FiberContext.unlock(FiberContext.scala:609)
      at zio.internal.FiberContext.unlock(FiberContext.scala:609)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.ZIO.run(ZIO.scala:1166)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at doobie.util.transactor$Transactor$fromDriverManager$.create(transactor.scala:321)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.internal.FiberContext.lock(FiberContext.scala:606)
      at zio.internal.FiberContext.lock(FiberContext.scala:606)
      at todo.serviceexamples.ZIOModule$Implementation$TodoServiceImpl$$anon$4.create(ZIOModule.scala:130)
      at zio.ZIO.unit(ZIO.scala:1447)
      at zio.Ref$.update$extension(Ref.scala:123)
      at todo.serviceexample.ZIOModuleSpec$$anonfun$$lessinit$greater$1.new(ZIOModuleSpec.scala:77)
      at todo.serviceexample.Fakes$.create(ZIOModuleSpec.scala:46)
      at zio.Ref$.make(Ref.scala:169)
      at zio.ZIOFunctions.effectSuspendTotal(ZIO.scala:2011)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.internal.FiberContext.evaluateNow(FiberContext.scala:531)
      at zio.ZIO.provideSomeManaged(ZIO.scala:835)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.test.environment.TestEnvironment$.Value(TestEnvironment.scala:104)
      at zio.Ref$.set$extension(Ref.scala:104)
      at zio.ZManaged.flatMap(ZManaged.scala:307)
      at zio.Ref$.update$extension(Ref.scala:123)
      at zio.ZManaged.flatMap(ZManaged.scala:307)
      at zio.ZManaged.map(ZManaged.scala:444)
      at zio.ZManaged.flatMap(ZManaged.scala:306)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:157)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.ZIOFunctions.bracketExit(ZIO.scala:1765)
      at zio.internal.FiberContext.evaluateNow(FiberContext.scala:535)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.ZIO.run(ZIO.scala:1166)
      at zio.internal.FiberContext$InterruptExit$.apply(FiberContext.scala:150)
      at zio.clock.Clock$Live$$anon$1.nanoTime(Clock.scala:43)
      at zio.clock.package$.nanoTime(clock.scala:44)
      at zio.ZIO.bracket_(ZIO.scala:147)
      at zio.internal.FiberContext.evaluateNow(FiberContext.scala:531)
      at zio.ZManaged.flatMap(ZManaged.scala:305)
      at zio.Ref$.update$extension(Ref.scala:123)
      at zio.ZManaged.flatMap(ZManaged.scala:305)
      at zio.ZManaged.flatMap(ZManaged.scala:307)
      at zio.Ref$.update$extension(Ref.scala:123)
      at zio.ZManaged.flatMap(ZManaged.scala:307)
      at zio.ZManaged.flatMap(ZManaged.scala:301)

    Fiber:Id(1579085394533,0) was spawned by: <empty trace>
 */
