package supervision

import akka.actor.{Props, Actor}

object SupervisionSample {

  class Supervisor extends Actor {
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._

    // TODO
    override val supervisorStrategy = ???

    def receive = {
      case p: Props => sender ! context.actorOf(p)
    }
  }

  class Supervisor2 extends Actor {
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._

    // TODO
    override val supervisorStrategy = ???

    @scala.throws[Exception](classOf[Exception])
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {}

    def receive = {
      case p: Props => sender() ! context.actorOf(p)
    }
  }

  class Child extends Actor {
    var state = 0
    // TODO
    def receive = {
      case ex: Exception => throw ex
      case "get" => sender ! state
      case i : Int => state += i
    }
  }
}
