package todo.model.auth

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.rho.swagger.models.{Model, ModelImpl, StringProperty}

case class AuthRequest(
                        username: String,
                        password: String
                     )

object AuthRequest {
  implicit val encoder: Encoder.AsObject[AuthRequest] = deriveEncoder[AuthRequest]
  implicit val decoder: Decoder[AuthRequest] = deriveDecoder[AuthRequest]

  val SwaggerModel: Set[Model] = Set(
    ModelImpl(
      id          = "AuthRequest",
      id2         = "AuthRequest",
      `type`      = Some("object"),
      description = Some("AuthRequest description"),
      name        = Some("AuthRequest"),
      properties = Map(
        "name" -> StringProperty(
          required    = true,
          description = Some("Username"),
          enums       = Set()
        ),
        "password" -> StringProperty(
          required    = true,
          description = Some("Password"),
          enums       = Set()
        )
      ),
      example = Some("""{ "username" : "Arkimedes", "password" : "hamster" }""")
    )
  )
}
