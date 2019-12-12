package todo.repos

import cats.effect.IO
import cats.syntax.functor._
import doobie.Transactor
import doobie.implicits._
import todo.model.auth.User

object Users {
  def usernameIsAvailable(username: String, transactor: Transactor[IO]): IO[Boolean] = {
    sql"select username from users where username = $username"
      .query[String]
      .to[List]
      .transact(transactor)
      .flatMap {
        case Nil => IO(true)
        case _ => IO(false)
      }
  }

  def createUser(user: User, transactor: Transactor[IO]): IO[Unit] = {
    sql"insert into users values (${user.username}, ${user.passwordHashed}, ${user.salt})"
      .update
      .run
      .transact(transactor)
      .void
  }

  def getUserByUsername(username: String, transactor: Transactor[IO]): IO[Option[User]] = {
    sql"select username, passwordHashed, salt from users where username = $username"
      .query[User]
      .to[List]
      .transact(transactor)
      .flatMap {
        case user :: Nil => IO(Some(user))
        case Nil => IO(None)
        case _ => throw new Exception(s"Multiple users of name $username in users table!")
      }
  }
}
