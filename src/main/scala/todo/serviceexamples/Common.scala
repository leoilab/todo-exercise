package todo.serviceexamples

import cats.effect.{IO, Sync}
import io.circe.generic.semiauto.deriveCodec
import doobie.free.connection.ConnectionIO
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import hotpotato.{Embedder, OneOf2}
import shapeless.ops.coproduct.Inject
import shapeless.{:+:, CNil, Coproduct}
import zio.{DefaultRuntime, UIO, ZIO}

import scala.collection.SortedMapLike
import scala.concurrent.ExecutionContext

object Common {

  implicit lazy val cs = IO.contextShift(ExecutionContext.global)
  lazy val transactor = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    "jdbc:sqlite:todo.db"
  )

  final case class Todo(
      id:   Int,
      name: String,
      done: Boolean
  )
  object Todo {
    implicit val circeCodec = deriveCodec[Todo]
  }

  type Trx[T] = ConnectionIO[T]

  object Trx {
    def unit: Trx[Unit] = Sync[ConnectionIO].unit
    def pure[T](value:       T):         Trx[T] = Sync[ConnectionIO].pure(value)
    def raiseError[T](error: Throwable): Trx[T] = Sync[ConnectionIO].raiseError(error)
  }

  sealed abstract class DomainError(message: String) extends Throwable(message)
  final case class InvalidId(id:             Int) extends DomainError(s"Invalid id: ${id}")
  final case class TodoNotFound(id:          Int) extends DomainError(s"Todo with id: ${id} not found")

  type FinishError   = InvalidId :+: TodoNotFound :+: CNil
  type FinishErrorHP = OneOf2[InvalidId, TodoNotFound]
  implicit val embedder = Embedder.make[FinishErrorHP]

  trait PartOfBigError[SmallError, BigError] {
    def embed(e: SmallError): BigError
  }

  implicit def head[S]: PartOfBigError[S, S :+: CNil] = new PartOfBigError[S, S :+: CNil] {
    def embed(e: S): S :+: CNil = Coproduct[S :+: CNil](e)
  }
  implicit def tail[S, B <: Coproduct](
      implicit inj: Inject[S :+: B, S]
  ): PartOfBigError[S, S :+: B] =
    new PartOfBigError[S, S :+: B] {
      def embed(e: S): S :+: B = Coproduct[S :+: B](e)
    }

  class Validator[B] {
    def option[T, S](op: Option[T], error: S)(implicit ev: PartOfBigError[S, B]): ZIO[Any, B, T] = {
      op match {
        case None    => ZIO.fail(ev.embed(error))
        case Some(x) => UIO(x)
      }
    }
  }
  object validate {
    def apply[B]: Validator[B] = new Validator[B]
  }

  def validateSimple[T, S, B](op: Option[T], error: S)(implicit ev: PartOfBigError[S, B]): ZIO[Any, B, T] = {
    op match {
      case None    => ZIO.fail(ev.embed(error))
      case Some(x) => UIO(x)
    }
  }

  def returnOptional(arg: String): UIO[Option[String]] = {
    UIO(if (arg == "empty") None else Some(s"YAY: ${arg}"))
  }

  val failed = for {
    maybeResult <- returnOptional("empty")
    result <- validate[FinishError].option(maybeResult, InvalidId(1))
  } yield result

  val success = for {
    maybeResult <- returnOptional("non-empty")
    result <- validate[FinishError].option(maybeResult, InvalidId(1))
  } yield result

  def main(args: Array[String]): Unit = {
    val runtime = new DefaultRuntime {}

    println(runtime.unsafeRun(success))
    println(runtime.unsafeRun(failed))
  }

  sealed trait UpdateResult
  object UpdateResult {
    case object Updated extends UpdateResult
    case object NotFound extends UpdateResult
  }

  sealed abstract class RepositoryError(message:  String) extends Throwable(message)
  final case class FailedToInsert(statement:      Fragment) extends RepositoryError(s"Failed to insert: ${statement}")
  final case class TooManyWriteResults(statement: Fragment, affectedRows: Int)
      extends RepositoryError(s"Too many write results(${affectedRows}) for: `${statement}`")
}
