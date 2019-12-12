package todo.model.todo

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Todo(
                 id:   Int,
                 name: String,
                 done: Boolean
               )

object Todo {
  implicit val encoder: Encoder.AsObject[Todo] = deriveEncoder[Todo]
  implicit val decoder: Decoder[Todo] = deriveDecoder[Todo]
}
