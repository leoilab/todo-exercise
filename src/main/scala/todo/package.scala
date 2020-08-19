import doobie.Transactor
import zio.{Has, Task}

package object todo {

  type Trx           = Transactor.Aux[Task, Unit]
  type Transactional = Has[Trx]

}
