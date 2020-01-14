package todo.serviceexample

import cats.effect.IO
import org.scalatest.FunSpec

trait IOTest { self: FunSpec =>

  def iot[A](message: String)(body: => IO[A]): Unit = {
    it(message) {
      body.unsafeRunSync()
    }
  }

}
