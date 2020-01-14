package todo

import cats.effect.IO
import cats.~>
import io.circe.{Encoder, Json}
import org.http4s.EntityEncoder
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import shapeless.{CNil, Inl, Inr}
import todo.Server.{CreateTodo, EmptyResponse, ErrorResponse}
import todo.serviceexamples.Common.{InvalidId, TodoNotFound}
import todo.serviceexamples.{FreeMonad, SimpleIO, Tagless}

object Routes {

  def simpleIO(service: SimpleIO.Service): RhoRoutes[IO] = {
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

  def tagless(
      // The name logger clashes with the logger defined in RhoRoutes
      implicit L: Tagless.Logger[IO],
      store:      Tagless.Store[IO],
      trx:        Tagless.TrxHandler[IO]
  ): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {

      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //
      implicit val errorResponseEntityEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
      implicit val emptyResponseEntityEncoder: EntityEncoder[IO, EmptyResponse] = jsonEncoderOf[IO, EmptyResponse]

      GET |>> { () =>
        Tagless.Implementation.Service.list.flatMap(Ok(_))
      }

      POST ^ jsonOf[IO, CreateTodo] |>> { createTodo: CreateTodo =>
        Tagless.Implementation.Service
          .create[IO](createTodo.name)
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / pathVar[Int] |>> { todoId: Int =>
        Tagless.Implementation.Service
          .finish[IO](todoId)
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

  def free(service: FreeMonad.Implementation.Service, interpreter: FreeMonad.Dsl ~> IO): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {

      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //
      implicit val errorResponseEntityEncoder: EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]
      implicit val emptyResponseEntityEncoder: EntityEncoder[IO, EmptyResponse] = jsonEncoderOf[IO, EmptyResponse]

      GET |>> { () =>
        service.list.foldMap(interpreter).flatMap(Ok(_))
      }

      POST ^ jsonOf[IO, CreateTodo] |>> { createTodo: CreateTodo =>
        service
          .create(createTodo.name)
          .foldMap(interpreter)
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / pathVar[Int] |>> { todoId: Int =>
        service
          .finish(todoId)
          .foldMap(interpreter)
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
