package todo

import cats.{Monad, MonadError}
import cats.effect.IO
import cats.syntax.apply._
import cats.syntax.functor._
import doobie.implicits._
import doobie.util.transactor.Transactor
import cats.implicits._
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import shapeless.{Inl, Inr}
import todo.Errors.{SomeOtherProblem, TodoNotFound}
import todo.Model.Todo
import todo.Server.{CreateTodo, EmptyResponse, ErrorResponse}

object Routes {
  def apply(service: TodoServiceIO): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {
      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //

      GET |>> { () =>
        service.list.flatMap(Ok(_))
      }

      POST ^ jsonOf[IO, CreateTodo] |>> { createTodo: CreateTodo =>
        service
          .create(createTodo.name)
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / pathVar[Int] |>> { todoId: Int =>
        service
          .finish(todoId)
          .map(Right(_): Either[TodoNotFound, Unit])
          .handleErrorWith {
            case TodoNotFound(id) => IO.pure(Left(TodoNotFound(id)): Either[TodoNotFound, Unit])
            case error            => IO.raiseError(error)
          }
          .flatMap {
            case Left(TodoNotFound(id)) => NotFound(ErrorResponse(s"Todo with id: ${todoId} not found"))
            case Right(_)               => Ok(EmptyResponse())
          }
//        sql"update todo set done = 1 where id = ${todoId}".update.run.transact(transactor).flatMap {
//          case 0 => NotFound(ErrorResponse(s"Todo with id: `${todoId}` not found"))
//          case 1 => Ok(EmptyResponse())
//          case _ =>
//            IO(println(s"Inconsistent data: More than one todo updated in POST /todo/${todoId}")) *>
//              InternalServerError(ErrorResponse("Ooops, something went wrong..."))
//        }
      }

      POST / pathVar[Int] / "typed-error" |>> { todoId: Int =>
        service
          .finishTypedError(todoId)
          .flatMap {
            case Left(TodoNotFound(id)) => NotFound(ErrorResponse(s"Todo with id: ${todoId} not found"))
            case Right(_)               => Ok(EmptyResponse())
          }
      }

      GET / "error-example" |>> {
        service.complexErrorExample.flatMap {
          case Left(Inl(TodoNotFound(id))) => NotFound(ErrorResponse(s"Todo with id: 1 not found"))
          case Left(Inr(Inl(SomeOtherProblem()))) =>
            BadRequest(ErrorResponse("Nonono"))
          case Left(Inr(Inr(cnil))) => cnil.impossible
          case Right(_)             => Ok(EmptyResponse())
        }
      }

    }
  }

  def applyF(implicit repository: TodoRepositoryF[IO], log: LoggerF[IO]): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {
      GET / "errors" |>> {
        TodoServiceF
        //          .create[IO]("asd")
        //          .finish
        //          .finishTypedError(1)
          .complexTypeError[IO]
          .flatMap {
            case Left(Inl(TodoNotFound(id))) => NotFound(ErrorResponse(s"Todo with id: 1 not found"))
            case Left(Inr(Inl(SomeOtherProblem()))) =>
              BadRequest(ErrorResponse("Nonono"))
            case Left(Inr(Inr(cnil))) => cnil.impossible
            case Right(_)             => Ok(EmptyResponse())
          }
      }
      GET / "errors2" |>> {
        val result = TodoServiceF.complexTypeError2[IO]

        result.value
          .flatMap {
            case Left(Inl(TodoNotFound(id))) => NotFound(ErrorResponse(s"Todo with id: 1 not found"))
            case Left(Inr(Inl(SomeOtherProblem()))) =>
              BadRequest(ErrorResponse("Nonono"))
            case Left(Inr(Inr(cnil))) => cnil.impossible
            case Right(_)             => Ok(EmptyResponse())
          }
      }
    }

  }
}
