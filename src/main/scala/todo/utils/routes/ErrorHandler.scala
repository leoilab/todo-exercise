package todo.utils.routes

import cats.effect.IO
import cats.syntax.apply._
import org.http4s.Request
import org.http4s.circe.CirceEntityEncoder

object ErrorHandler extends CirceEntityEncoder {
  import Responses.ErrorResponse

  // NOTE: This import clashes with a lot of rho names hence the wrapper object
  import org.http4s.dsl.io._

  def apply(request: Request[IO]): PartialFunction[Throwable, IO[org.http4s.Response[IO]]] = {
    case ex: Throwable =>
      IO(println(s"UNHANDLED: $ex\n${ex.getStackTrace.mkString("\n")}")) *>
        InternalServerError(ErrorResponse("Something went wrong"))
  }
}
