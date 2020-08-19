package todo

import io.circe.generic.semiauto.deriveCodec

object Models {

  final case class Todo(
      id:   Int,
      name: String,
      done: Boolean
  )

  object Todo {
    implicit val circeCodec = deriveCodec[Todo]
  }

  final case class CreateTodo(name: String)

  object CreateTodo {
    implicit val circeCodec = deriveCodec[CreateTodo]
  }

  final case class EmptyResponse()

  object EmptyResponse {
    implicit val circeCodec = deriveCodec[EmptyResponse]
  }

  final case class ErrorResponse(message: String)

  object ErrorResponse {
    implicit val circeCodec = deriveCodec[ErrorResponse]
  }

}
