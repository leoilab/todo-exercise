package todo.serviceexamples

import zio.ZIO

object ZIOModule {

  trait Logger {
    val logger: Logger.Service[Any]
  }
  object Logger {
    trait Service[R] {
      def info(message: String): ZIO[R, Nothing, Unit]
    }
  }

}
