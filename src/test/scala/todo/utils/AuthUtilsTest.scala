package todo.utils

import org.scalatest._
import todo.model.auth.AuthRequest
import todo.utils.auth.AuthUtils
import todo.utils.auth.AuthUtils.{UsernameError, PasswordError}

class SanitycheckAuthRequestTests extends FunSuite with Matchers {

  private val badPasswords = Seq(
    AuthRequest("wellformedname", "pshort"),
    AuthRequest("wellformedname", "fartoolongpassword"),
    AuthRequest("wellformedname", "nønåsciiletters"),
    AuthRequest("wellformedname", "white space"),
  )

  private val badUsernames = Seq(
    AuthRequest("tinytim", "hamster1"),
    AuthRequest("marvelousmsmaisell", "hamster1"),
    AuthRequest("ågehænning", "hamster1"),
    AuthRequest("hr sjov", "hamster1"),
  )

  for (authRequest <- badPasswords) {
    test(s"SanitycheckAuthRequest should return Left($PasswordError) for password ${authRequest.password}") {
      AuthUtils.SanitycheckAuthRequest(authRequest) shouldEqual Left(PasswordError)
    }
  }

  for (authRequest <- badUsernames) {
    test(s"SanitycheckAuthRequest should return Left($UsernameError) for username ${authRequest.username}") {
      AuthUtils.SanitycheckAuthRequest(authRequest) shouldEqual Left(UsernameError)
    }
  }
}

class hashPasswordTest extends FlatSpec {
  "hashPassword" should "generate two different hashes if given the same password" in {
    val badPassword = "password"
    val (hash1, _) = AuthUtils.hashPassword(badPassword)
    val (hash2, _) = AuthUtils.hashPassword(badPassword)

    assert(hash1 != hash2)
  }
}
