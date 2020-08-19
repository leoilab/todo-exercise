package todo

import java.util.concurrent.Executors

import cats.implicits._
import cats.effect.{ConcurrentEffect, Timer}
import doobie.implicits._
import org.http4s.implicits._
import org.http4s.HttpRoutes
import org.http4s.server.blaze.BlazeServerBuilder
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s._
import scala.concurrent.ExecutionContext
import todo.Models._
import zio._
import zio.interop.catz._

object Server {

  def run(implicit cs: ConcurrentEffect[Task], t: Timer[Task]): URIO[Transactional, Unit] = {
    val serverThreadPool = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

    Endpoints.createHttp4sRoutes.flatMap { routes =>
      BlazeServerBuilder
        .apply[Task](serverThreadPool)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(routes.orNotFound)
        .resource
        .use(_ => UIO.never)
        .orDie
    }
  }

  object Endpoints {
    val listTodos: Endpoint[Unit, Unit, List[Todo], Nothing] = {
      endpoint.get
        .in("todo")
        .out(jsonBody[List[Todo]])
    }

    val createTodo: Endpoint[CreateTodo, Unit, EmptyResponse, Nothing] = {
      endpoint.post
        .in("todo")
        .in(jsonBody[CreateTodo])
        .out(jsonBody[EmptyResponse])
    }

    val finishTodo: Endpoint[Int, ErrorResponse, EmptyResponse, Nothing] = {
      endpoint.put
        .in("todo" / path[Int]("id"))
        .out(jsonBody[EmptyResponse])
        .errorOut(jsonBody[ErrorResponse])
    }

    def createHttp4sRoutes: URIO[Transactional, HttpRoutes[Task]] = {
      ZIO.service[Trx].map { transactor =>
        val listRoute = listTodos.toRoutes { _ =>
          sql"select id, name, done from todo"
            .query[Todo]
            .to[List]
            .transact(transactor)
            .map[Either[Unit, List[Todo]]](Right(_))
        }

        val createRoute = createTodo.toRoutes { createDto =>
          sql"insert into todo (name, done) values (${createDto.name}, 0)".update.run
            .transact(transactor)
            .unit
            .as[Either[Unit, EmptyResponse]](Right(EmptyResponse()))
        }

        val finishRoute = finishTodo.toRoutes { id =>
          sql"update todo set done = 1 where id = ${id}".update.run
            .transact(transactor)
            .flatMap {
              case 0 => ZIO.succeed(Left(ErrorResponse(s"Todo with id: `${id}` not found")))
              case 1 => ZIO.succeed(Right(EmptyResponse()))
              case _ =>
                ZIO
                  .effect(println(s"Inconsistent data: More than one todo updated in POST /todo/${id}"))
                  .as(Left(ErrorResponse("Ooops, something went wrong...")))
            }
        }

        listRoute <+> createRoute <+> finishRoute
      }
    }
  }

}
