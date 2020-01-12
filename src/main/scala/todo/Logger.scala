package todo

import cats.effect.IO

trait LoggerIO {
  def logInfo(message:  String): IO[Unit]
  def logError(message: String, cause: Throwable): IO[Unit]
}

class LoggerIOImpl extends LoggerIO {
  def logInfo(message: String): IO[Unit] = {
    IO(println(s"[INFO] ${message}"))
  }

  def logError(message: String, cause: Throwable): IO[Unit] = {
    IO(println(s"[ERROR] ${message} caused by: ${cause}"))
  }
}

trait LoggerF[F[_]] {
  def logInfo(message:  String): F[Unit]
  def logError(message: String, cause: Throwable): F[Unit]
}
object LoggerF {
  type LoggerFIO = LoggerF[IO]
  @inline def apply[F[_]](implicit instance: LoggerF[F]): LoggerF[F] = instance
}

class LoggerFImplForIO extends LoggerIOImpl with LoggerF[IO]
