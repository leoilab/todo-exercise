package todo.utils.auth

import java.time.Clock

import org.reactormonk.{CryptoBits, PrivateKey}

object AuthTokenUtils {
  val key: PrivateKey = PrivateKey(scala.io.Codec.toUTF8(scala.util.Random.alphanumeric.take(20).mkString("")))
  val crypto: CryptoBits = CryptoBits(key)
  val clock: Clock = Clock.systemUTC

  def createToken(username: String): String ={
    crypto.signToken(username, clock.millis.toString)
  }

  def validateToken(token: String): Option[String] ={
    crypto.validateSignedToken(token)
  }
}
