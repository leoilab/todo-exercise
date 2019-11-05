package todo

import cats.effect.{ContextShift, IO, Timer}
import org.http4s.rho.swagger.models.{
  ApiKeyAuthDefinition,
  In,
  Info,
  Model,
  ModelImpl,
  Scheme,
  SecurityRequirement,
  StringProperty
}
import cats.syntax.functor._
import cats.syntax.apply._
import doobie.Transactor
import doobie.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.{HttpRoutes, Request}
import org.http4s.circe.{CirceEntityEncoder, CirceInstances}
import org.http4s.rho.{RhoMiddleware, RhoRoutes}
import org.http4s.rho.swagger.{DefaultSwaggerFormats, SwaggerSupport, SwaggerSyntax}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.staticcontent.{fileService, FileService}

import scala.reflect.runtime.universe.typeOf

object Server {

  val todoApiInfo = Info(
    title   = "TODO API",
    version = "0.1.0"
  )
  val host     = "localhost"
  val port     = 8080
  val basePath = "/v1"

  case class Todo(
      id:   Int,
      name: String,
      done: Boolean
  )

  object Todo {
    implicit val encoder = deriveEncoder[Todo]
    implicit val decoder = deriveDecoder[Todo]
  }

  case class CreateTodo(name: String)

  object CreateTodo {
    implicit val encoder = deriveEncoder[CreateTodo]
    implicit val decoder = deriveDecoder[CreateTodo]

    val SwaggerModel: Set[Model] = Set(
      ModelImpl(
        id          = "CreateTodo",
        id2         = "CreateTodo",
        `type`      = Some("object"),
        description = Some("CreateTodo description"),
        name        = Some("CreateTodo"),
        properties = Map(
          "name" -> StringProperty(
            required    = true,
            description = Some("name of the todo item"),
            enums       = Set()
          )
        ),
        example = Some("""{ "name" : "todo 1" }""")
      )
    )
  }

  case class EmptyResponse()

  object EmptyResponse {
    implicit val encoder = deriveEncoder[EmptyResponse]
    implicit val decoder = deriveDecoder[EmptyResponse]
  }

  case class ErrorResponse(message: String)

  object ErrorResponse {
    implicit val encoder = deriveEncoder[ErrorResponse]
    implicit val decoder = deriveDecoder[ErrorResponse]
  }

  object ErrorHandler extends CirceEntityEncoder {
    // NOTE: This import clashes with a lot of rho names hence the wrapper object
    import org.http4s.dsl.io._

    def apply(request: Request[IO]): PartialFunction[Throwable, IO[org.http4s.Response[IO]]] = {
      case ex: Throwable =>
        IO(println(s"UNHANDLED: ${ex}\n${ex.getStackTrace.mkString("\n")}")) *>
          InternalServerError(ErrorResponse("Something went wrong"))
    }
  }

  def run(transactor: Transactor[IO])(implicit cs: ContextShift[IO], t: Timer[IO]): IO[Unit] = {
    // NOTE: the import is necessary to get .orNotFound but clashes with a lot of rho names that's why it's imported inside the method
    import org.http4s.implicits._
    BlazeServerBuilder[IO]
      .bindHttp(port, host)
      .withHttpApp(createRoutes(transactor).orNotFound)
      .withServiceErrorHandler(ErrorHandler(_))
      .resource
      .use(_ => IO.never)
  }

  def createRoutes(transactor: Transactor[IO])(implicit cs: ContextShift[IO]): HttpRoutes[IO] = {
    val todoRoutes        = createTodoRoutes(transactor)
    val swaggerMiddleware = createSwaggerMiddleware

    Router(
      "/docs"  -> fileService[IO](FileService.Config[IO]("./swagger")),
      basePath -> todoRoutes.toRoutes(swaggerMiddleware)
    )
  }

  def createTodoRoutes(transactor: Transactor[IO]): RhoRoutes[IO] = {
    new RhoRoutes[IO] with SwaggerSyntax[IO] with CirceInstances with CirceEntityEncoder {
      // ----------------------------------------------------------------------------------------------------------------------- //
      //  NOTE: If you run into issues with divergent implicits check out this issue https://github.com/http4s/rho/issues/292   //
      // ---------------------------------------------------------------------------------------------------------------------- //

      GET / "todo" |>> { () =>
        sql"select id, name, done from todo".query[Todo].to[List].transact(transactor).flatMap(Ok(_))
      }

      POST / "todo" ^ jsonOf[IO, CreateTodo] |>> { createTodo: CreateTodo =>
        sql"insert into todo (name, done) values (${createTodo.name}, 0)".update.run
          .transact(transactor)
          .void
          .flatMap(_ => Ok(EmptyResponse()))
      }

      POST / "todo" / pathVar[Int] |>> { todoId: Int =>
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

  def createSwaggerMiddleware: RhoMiddleware[IO] = {
    SwaggerSupport
      .apply[IO]
      .createRhoMiddleware(
        swaggerFormats = DefaultSwaggerFormats
          .withSerializers(typeOf[CreateTodo], CreateTodo.SwaggerModel),
        apiInfo  = todoApiInfo,
        host     = Some(s"${host}:${port}"),
        schemes  = List(Scheme.HTTP),
        basePath = Some(basePath),
        security = List(SecurityRequirement("Bearer", List())),
        securityDefinitions = Map(
          "Bearer" -> ApiKeyAuthDefinition("Authorization", In.HEADER)
        )
      )
  }

}
