package todo.repos

import cats.effect.IO
import doobie.util.transactor.Transactor.Aux
import org.scalatest._
import todo.Migrations
import todo.model.auth.User

class UsersTest extends FunSuite {
  // Setup for tests
  val transactor: Aux[IO, Unit] = todo.Main.inmemoryTransactor()
  Migrations.run(transactor)

  test("We can create a user and retrieve it afterwards"){
    val username = "Somename"
    val hashedPassword = "xxxdeadb33f"
    val salt = "nicesalt"
    val user = User(username, hashedPassword, salt)

    val retrievedUser = todo.repos.Users.createUser(user, transactor).flatMap { _ =>
      todo.repos.Users.getUserByUsername(user.username, transactor)
    }.unsafeRunSync()

    assert(user == retrievedUser.get)
  }
}
