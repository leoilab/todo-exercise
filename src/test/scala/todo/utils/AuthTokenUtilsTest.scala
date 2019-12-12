package todo.utils

import org.scalatest._

class AuthTokenUtilsTest extends FlatSpec {
  import todo.utils.auth.AuthTokenUtils.{createToken, validateToken}
  "validateToken(createToken(username)" should "return Some (username)" in {
    val username = "myfairusername"
    val username_ = validateToken(createToken(username)).get

    assert(username == username_)
  }
}
