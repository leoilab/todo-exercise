package todo.repos

import cats.effect.IO
import cats.syntax.functor._
import doobie.Transactor
import doobie.implicits._
import todo.model.todo.Todo

object Todos {
  def getUserTodos(username: String, transactor: Transactor[IO]): IO[List[Todo]] = {
    sql"select id, name, done from todo where uesrname = $username"
      .query[Todo]
      .to[List]
      .transact(transactor)
  }

  def insertTodo(username: String, name: String, transactor: Transactor[IO]): IO[Unit] = {
    sql"insert into todo (username, name, done) values ($username, $name, 0)"
      .update
      .run
      .transact(transactor)
      .void
  }

  sealed trait MarkTodoDoneResult
  case object NotFound extends MarkTodoDoneResult
  case object Success extends MarkTodoDoneResult
  case object MoreThanOneFound extends MarkTodoDoneResult

  def markTodoDone(username: String, todoId: Int, transactor: Transactor[IO]): IO[MarkTodoDoneResult] = {
    sql"update todo set done = 1 where id = $todoId and username = $username"
      .update
      .run
      .transact(transactor)
      .flatMap {
        case 0 => IO(NotFound)
        case 1 => IO(Success)
        case _ => IO(MoreThanOneFound)
      }
  }







}
