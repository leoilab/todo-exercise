package todo

import cats.effect.{ContextShift, IO, Timer}
import doobie.Transactor
import org.http4s.HttpRoutes
import org.http4s.rho.RhoMiddleware
import org.http4s.rho.swagger.models._
import org.http4s.rho.swagger.{DefaultSwaggerFormats, SwaggerSupport}
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.staticcontent.{FileService, fileService}
import todo.routes.{AuthRoutes, TodoRoutes}
import todo.utils.routes.ErrorHandler

import scala.reflect.runtime.universe.typeOf

object Server {

  val todoApiInfo: Info = Info(
    title   = "TODO API",
    version = "0.1.0"
  )
  val host     = "localhost"
  val port     = 8080
  val basePath = "/v2"

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
    val todoRoutes        = TodoRoutes.createTodoRoutes(transactor)
    val authRoutes        = AuthRoutes.createAuthRoutes(transactor)

    val swaggerMiddleware = createSwaggerMiddleware

    Router(
      "/docs"  -> fileService[IO](FileService.Config[IO]("./swagger")),
      basePath -> todoRoutes.toRoutes(swaggerMiddleware),
      basePath -> authRoutes.toRoutes(swaggerMiddleware)
    )
  }

  def createSwaggerMiddleware: RhoMiddleware[IO] = {
    import todo.model.auth.AuthRequest
    import todo.model.todo.CreateTodo

    SwaggerSupport
      .apply[IO]
      .createRhoMiddleware(
        swaggerFormats = DefaultSwaggerFormats
          .withSerializers(typeOf[CreateTodo], CreateTodo.SwaggerModel)
          .withSerializers(typeOf[AuthRequest], AuthRequest.SwaggerModel),
        apiInfo  = todoApiInfo,
        host     = Some(s"$host:$port"),
        schemes  = List(Scheme.HTTP),
        basePath = Some(basePath),
        security = List(SecurityRequirement("Bearer", List())),
        securityDefinitions = Map(
          "Bearer" -> ApiKeyAuthDefinition("Authorization", In.HEADER)
        )
      )
  }

}
