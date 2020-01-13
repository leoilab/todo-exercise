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
import todo.serviceexamples.SimpleIO

import scala.reflect.runtime.universe.typeOf

object Server {

  val todoApiInfo = Info(
    title   = "TODO API",
    version = "0.1.0"
  )
  val host     = "localhost"
  val port     = 8080
  val basePath = "/v1"
  val todoPath = "/todo"

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

  def createRoutes(transactor: Transactor[IO])(
      implicit cs:             ContextShift[IO]
  ): HttpRoutes[IO] = {
    val todoRepositoryIO  = new SimpleIO.Implementation.StoreImpl(transactor)
    val logger            = SimpleIO.Implementation.LoggerImpl
    val trx               = new SimpleIO.Implementation.TrxHandlerImpl(transactor)
    val todoServiceIO     = new SimpleIO.Implementation.ServiceImpl(logger, todoRepositoryIO, trx)
    val todoRoutes        = Routes(todoServiceIO)
    val swaggerMiddleware = createTodoSwaggerMiddleware

    Router(
      "/docs"             -> fileService[IO](FileService.Config[IO]("./swagger")),
      basePath + todoPath -> todoRoutes.toRoutes(swaggerMiddleware)
    )
  }

  def createTodoSwaggerMiddleware: RhoMiddleware[IO] = {
    SwaggerSupport
      .apply[IO]
      .createRhoMiddleware(
        swaggerFormats = DefaultSwaggerFormats
          .withSerializers(typeOf[CreateTodo], CreateTodo.SwaggerModel),
        apiInfo  = todoApiInfo,
        host     = Some(s"${host}:${port}"),
        schemes  = List(Scheme.HTTP),
        basePath = Some(basePath + todoPath),
        security = List(SecurityRequirement("Bearer", List())),
        securityDefinitions = Map(
          "Bearer" -> ApiKeyAuthDefinition("Authorization", In.HEADER)
        )
      )
  }

}
