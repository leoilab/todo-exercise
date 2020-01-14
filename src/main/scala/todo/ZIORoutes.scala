package todo

import zio.interop.catz._
import org.http4s.EntityEncoder
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import shapeless.{Inl, Inr}
import todo.Server.{CreateTodo, EmptyResponse, ErrorResponse}
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.{FreeMonad, SimpleIO, Tagless, ZIOModule}
import zio.Task

object ZIORoutes {

  def apply(service: ZIOModule.TodoService.Service[Any]): RhoRoutes[Task] = {
    new RhoRoutes[Task] with SwaggerSyntax[Task] with CirceInstances with CirceEntityEncoder {

      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //
      implicit val errorResponseEntityEncoder: EntityEncoder[Task, ErrorResponse] = jsonEncoderOf[Task, ErrorResponse]
      implicit val emptyResponseEntityEncoder: EntityEncoder[Task, EmptyResponse] = jsonEncoderOf[Task, EmptyResponse]

      GET |>> { () =>
        service.list.flatMap(Ok(_))
      }

      POST ^ jsonOf[Task, CreateTodo] |>> { createTodo: CreateTodo =>
        service
          .create(createTodo.name)
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / pathVar[Int] |>> { todoId: Int =>
        service
          .finish(todoId)
          .either
          .flatMap {
            case Left(Inl(InvalidId(id))) =>
              BadRequest(ErrorResponse(s"Invalid id: ${id}"))
            case Left(Inr(Inl(TodoNotFound(id)))) =>
              NotFound(ErrorResponse(s"Todo with id: 1 not found"))
            case Left(Inr(Inr(cnil))) => cnil.impossible
            case Right(_)             => Ok(EmptyResponse())
          }
      }

    }
  }

}
