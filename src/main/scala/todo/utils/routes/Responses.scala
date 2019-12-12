package todo.utils.routes

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object Responses {
  case class EmptyResponse()
  object EmptyResponse {
    implicit val encoder: Encoder.AsObject[EmptyResponse] = deriveEncoder[EmptyResponse]
    implicit val decoder: Decoder[EmptyResponse] = deriveDecoder[EmptyResponse]
  }


  case class ErrorResponse(message: String)
  object ErrorResponse {
    implicit val encoder: Encoder.AsObject[ErrorResponse] = deriveEncoder[ErrorResponse]
    implicit val decoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  }

}
