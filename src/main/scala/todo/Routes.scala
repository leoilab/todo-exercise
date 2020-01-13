package todo

import cats.effect.IO
import io.circe.{Encoder, Json}
import org.http4s.EntityEncoder
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import shapeless.{CNil, Inl, Inr}
import todo.Server.{CreateTodo, EmptyResponse, ErrorResponse}
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.SimpleIO

object Routes {
  implicit val cnilEncoder = new Encoder[CNil] {
    def apply(a: CNil): Json = throw new Exception("The impossible has happened!")
  }
  def apply(service: SimpleIO.Service): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {

      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //
      implicit val errorResponseEntityEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
      implicit val emptyResponseEntityEncoder: EntityEncoder[IO, EmptyResponse] = jsonEncoderOf[IO, EmptyResponse]

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
          .value
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
