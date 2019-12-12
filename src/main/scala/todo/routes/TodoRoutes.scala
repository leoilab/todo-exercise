package todo.routes

import cats.data.Kleisli
import org.http4s.dsl.io._
import cats.effect.{ContextShift, IO, Timer}
import doobie.Transactor
import doobie.implicits._
import cats.syntax.functor._
import cats.syntax.apply._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.{AuthedRoutes, AuthedService, Request}
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.dsl.impl.Root
import todo.model.auth.{AuthToken, User}
import todo.model.todo.{CreateTodo, Todo}
import todo.repos.Todos


object TodoRoutes {
  /** The [[AuthedContext]] provides a convenient way to define a RhoRoutes
   * which works with http4s authentication middleware.
   * Please note that `AuthMiddleware`-wrapping is mandatory, otherwise context
   * doesn't take effect.
   * {{{
   *     case class User(name: String, id: UUID)
   *
   *     val authUser: Kleisli[IO, Request[IO], Either[String, User]] = Kleisli{ req =>
   *       IO(Right(User("Bob", UUID.randomUUID())))
   *     }
   *
   *     val onFailure: AuthedRoutes[String, IO] = Kleisli(req => OptionT.liftF(Forbidden(req.authInfo)))
   *
   *     val middleware = AuthMiddleware(authUser, onFailure)
   *
   *     object Auth extends AuthedContext[IO, User]
   *
   *     object BobRoutes extends RhoRoutes[IO] {
   *       GET +? param("foo", "bar") >>> Auth.auth |>> { (foo: String, user: User) =>
   *         Ok(s"Bob with id \${user.id}, foo \$foo")
   *       }
   *     }
   *
   *     val service = middleware.apply(Auth.toService(BobRoutes.toRoutes()))
   * }}}
   * **/
/*
  val authUser: Kleisli[IO, Request[IO], Either[String, AuthToken]] = Kleisli{ req =>
    IO(Right(User("Bob", UUID.randomUUID())))
  }
*/

  object Auth extends org.http4s.rho.AuthedContext[IO, AuthToken]

  def createTodoRoutes(transactor: Transactor[IO]): RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder = {
    import todo.utils.routes.Responses.{EmptyResponse, ErrorResponse}
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {
      GET / "todo" |>> { () =>
        Todos.getUserTodos("", transactor)
          .flatMap { Ok(_) }
      }

      POST / "todo" ^ jsonOf[IO, CreateTodo] |>> { createTodo: CreateTodo =>
        Todos.insertTodo("", createTodo.name, transactor)
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / "todo" / pathVar[Int] |>> { todoId: Int =>
        Todos.markTodoDone("", todoId, transactor).flatMap {
          case Todos.NotFound => InternalServerError(ErrorResponse(s"Todo with id: `$todoId` not found"))
          case Todos.MoreThanOneFound =>
            IO(println(s"Inconsistent data: More than one todo updated in POST /todo/$todoId")) *>
              InternalServerError(ErrorResponse("Ooops, something went wrong..."))
          case Todos.Success => Ok(EmptyResponse())
        }
      }
    }
    // ----------------------------------------------------------------------------------------------------------------------- //
    //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
    // ---------------------------------------------------------------------------------------------------------------------- //
  }
}
