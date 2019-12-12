package todo.utils.auth

import java.security.MessageDigest

import todo.model.auth.AuthRequest

import scala.util.Random
import scala.util.matching.Regex

object AuthUtils {

  val UsernameError: String = "Username must consist of between 8 to 16 characters/digits (a-z, A-Z, 0-9)"
  val PasswordError: String = "Password must consist of between 8 to 16 characters/digits (a-z, A-Z, 0-9)"

  private[this] val AuthRequestRegex: Regex = new Regex("\\A[a-zA-Z0-9]{8,16}\\z$")
  private[this] val SaltLength = 16

  private[this] def SanitycheckUsername(authRequest: AuthRequest): Either[String, Unit] = {
    AuthRequestRegex.findFirstIn(authRequest.username) match {
      case Some(_) => Right ()
      case None => Left(UsernameError)
    }
  }

  private[this] def SanitycheckPassword(authRequest: AuthRequest): Either[String, Unit] = {
    AuthRequestRegex.findFirstIn(authRequest.password) match {
      case Some(_) => Right ()
      case None => Left(PasswordError)
    }
  }

  private[this] def getRandomSalt: String = {
    Random.nextString(SaltLength)
  }

  def SanitycheckAuthRequest(authRequest: AuthRequest): Either[String, Unit] = {
    SanitycheckUsername(authRequest).flatMap(_ => SanitycheckPassword(authRequest))
  }

  def hashPasswordWithSalt(password: String, salt: String): (String, String) = {
    /* Implementation below is borrowed from StackOverflow */
    val hashedPassword = MessageDigest.getInstance("SHA-256")
      .digest(s"$password$salt".getBytes("UTF-8"))
      .map("%02x".format(_)).mkString

    (hashedPassword, salt)
  }

  def hashPassword(password: String): (String, String) = {
    val salt = getRandomSalt
    hashPasswordWithSalt(password, salt)
  }


}
