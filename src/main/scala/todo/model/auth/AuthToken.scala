package todo.model.auth

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AuthToken(
                      token: String
                      )

object AuthToken {
  implicit val encoder: Encoder.AsObject[AuthToken] = deriveEncoder[AuthToken]
  implicit val decoder: Decoder[AuthToken] = deriveDecoder[AuthToken]
}
