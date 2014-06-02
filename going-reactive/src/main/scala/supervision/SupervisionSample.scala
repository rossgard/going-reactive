package supervision

import akka.actor.{Props, Actor}

object SupervisionSample {

  class Supervisor extends Actor {
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._

    override val supervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: ArithmeticException      => Resume
        case _: NullPointerException     => Restart
        case _: IllegalArgumentException => Stop
        case _: Exception                => Escalate
      }

    def receive = {
      case p: Props => sender ! context.actorOf(p)
    }
  }

  class Supervisor2 extends Actor {
    import akka.actor.OneForOneStrategy
    import akka.actor.SupervisorStrategy._
    import scala.concurrent.duration._

    override val supervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
        case _: ArithmeticException      => Resume
        case _: NullPointerException     => Restart
        case _: IllegalArgumentException => Stop
        case _: Exception                => Escalate
      }

    def receive = {
      case p: Props => sender() ! context.actorOf(p)
    }
    // override default to kill all children during restart
    override def preRestart(cause: Throwable, msg: Option[Any]) {}

    override def preStart(): Unit = super.preStart()

    override def postStop(): Unit = super.postStop()

    override def postRestart(reason: Throwable): Unit = super.postRestart(reason)
  }

  class Child extends Actor {
    var state = 0
    def receive = {
      case ex: Exception => throw ex
      case x: Int        => state = x
      case "get"         => sender ! state
    }
  }
}