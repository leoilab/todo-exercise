package todo

import cats.effect.IO
import todo.Model.Todo

trait TodoService {
  def list: IO[Vector[Todo]]
  def create(name: String): IO[Unit]
  def finish(id:   Int):    IO[Unit]
}

final class TodoServiceImpl(todoRepository: TodoRepository) extends TodoService {
  def list: IO[Vector[Todo]] = {
    todoRepository.list
  }

  def create(name: String): IO[Unit] = {
    todoRepository.create(name)
  }

  def finish(id: Int): IO[Unit] = {
    todoRepository.finish(id)
  }
}
