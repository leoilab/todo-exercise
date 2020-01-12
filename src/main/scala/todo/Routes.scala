package todo

import cats.effect.IO
import cats.syntax.apply._
import cats.syntax.functor._
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import todo.Model.Todo
import todo.Server.{CreateTodo, EmptyResponse, ErrorResponse}

object Routes {
  def apply(transactor: Transactor[IO]): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {
      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //

      GET |>> { () =>
        sql"select id, name, done from todo".query[Todo].to[List].transact(transactor).flatMap(Ok(_))
      }

      POST ^ jsonOf[IO, CreateTodo] |>> { createTodo: CreateTodo =>
        sql"insert into todo (name, done) values (${createTodo.name}, 0)".update.run
          .transact(transactor)
          .void
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / pathVar[Int] |>> { todoId: Int =>
        sql"update todo set done = 1 where id = ${todoId}".update.run.transact(transactor).flatMap {
          case 0 => NotFound(ErrorResponse(s"Todo with id: `${todoId}` not found"))
          case 1 => Ok(EmptyResponse())
          case _ =>
            IO(println(s"Inconsistent data: More than one todo updated in POST /todo/${todoId}")) *>
              InternalServerError(ErrorResponse("Ooops, something went wrong..."))
        }
      }
    }

  }
}
