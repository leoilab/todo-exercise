package todo

import cats.effect.IO
import todo.Model.Todo

trait TodoRepository {
  def list: IO[Vector[Todo]]
  def create(name: String): IO[Unit]
  def finish(id:   Int):    IO[Unit]
}
