package todo.model.todo

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.http4s.rho.swagger.models.{Model, ModelImpl, StringProperty}

case class CreateTodo(name: String)

object CreateTodo {
  implicit val encoder: Encoder.AsObject[CreateTodo] = deriveEncoder[CreateTodo]
  implicit val decoder: Decoder[CreateTodo] = deriveDecoder[CreateTodo]

  val SwaggerModel: Set[Model] = Set(
    ModelImpl(
      id          = "CreateTodo",
      id2         = "CreateTodo",
      `type`      = Some("object"),
      description = Some("CreateTodo description"),
      name        = Some("CreateTodo"),
      properties = Map(
        "name" -> StringProperty(
          required    = true,
          description = Some("name of the todo item"),
          enums       = Set()
        )
      ),
      example = Some("""{ "name" : "todo 1" }""")
    )
  )
}
