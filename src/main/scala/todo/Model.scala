package todo

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object Model {

  case class Todo(
      id:   Int,
      name: String,
      done: Boolean
  )

  object Todo {
    implicit val encoder = deriveEncoder[Todo]
    implicit val decoder = deriveDecoder[Todo]
  }

}
