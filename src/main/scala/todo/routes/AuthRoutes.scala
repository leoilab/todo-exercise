package todo.routes

import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.rho.bits._
import cats.effect.IO
import cats.syntax.apply._
import cats.syntax.functor._
import doobie.Transactor
import doobie.implicits._
import org.http4s.HttpDate
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax
import todo.model.auth.{AuthRequest, AuthToken, User}
import todo.repos.Users
import todo.utils.auth.{AuthTokenUtils, AuthUtils}

object AuthRoutes {
  private[this] val GenericLogonError = "Username or Password doesn't exist"

  def createAuthRoutes(transactor: Transactor[IO]): RhoRoutes[IO] = {
    import todo.utils.routes.Responses.{EmptyResponse, ErrorResponse}
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {
      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //

      POST / "auth" / "new" ^ jsonOf[IO, AuthRequest] |>> { authRequest: AuthRequest =>
        AuthUtils.SanitycheckAuthRequest(authRequest) match {
          case Left(errorMessage) =>
            IO(println(s"User creation attempt in POST /auth/new caused error $errorMessage.")) *>
              InternalServerError(ErrorResponse(errorMessage))

          case Right(_) =>
            Users.usernameIsAvailable(authRequest.username, transactor).flatMap {
              case false =>
                IO(println(s"User creation attempt in POST /auth/new tried to create existing username.")) *>
                  InternalServerError(ErrorResponse("Username already exists!"))

              case true =>
                val (hashedPassword, salt) = AuthUtils.hashPassword(authRequest.password)
                val user = User(authRequest.username, hashedPassword, salt)
                Users.createUser(user, transactor)
                  .flatMap { _ =>
                    Ok(EmptyResponse())
                  }
            }
        }
      }

      POST / "auth" ^ jsonOf[IO, AuthRequest] |>> { authRequest: AuthRequest => {
        AuthUtils.SanitycheckAuthRequest(authRequest) match {
          case Left(errorMessage) =>
            IO(println(s"User logon attempt in POST /auth caused error $errorMessage.")) *>
              InternalServerError(ErrorResponse(errorMessage))
          case Right(_) =>

            Users.getUserByUsername(authRequest.username, transactor).flatMap {
              case None => IO(println(s"User attempted login with non-existing username ${authRequest.username}.")) *>
                InternalServerError(ErrorResponse(GenericLogonError))
              case Some (user) =>

                val (requestPasswordHashed, _) = AuthUtils.hashPasswordWithSalt(authRequest.password, user.salt)
                if (requestPasswordHashed == user.passwordHashed) {
                  val authToken = AuthToken(AuthTokenUtils.createToken(user.username))
                  Ok(authToken)

                } else {
                  IO(println(s"User ${authRequest.username} attempted login with wrong password.")) *>
                    InternalServerError(ErrorResponse(GenericLogonError))
                }
            }
        }
      }
      }
    }
  }
}
