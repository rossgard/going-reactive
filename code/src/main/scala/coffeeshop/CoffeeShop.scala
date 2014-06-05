package coffeeshop

import akka.actor._

object Barista {
  sealed trait CoffeeRequest

  object EspressoRequest extends CoffeeRequest
  object CappuccinoRequest extends CoffeeRequest

  case class Bill(cents: Int)
  case class ServedCoffeeRequests(espressoCount: Int, cappuccinoCount: Int)

  object GetServedCoffeeRequests
  object LunchBreak

  object Work
  object Lunch
  object CloseShop
}

class Barista extends Actor with ActorLogging {
  import akka.event.LoggingReceive
  import coffeeshop.Barista._

  var cappuccinoCount, espressoCount = 0

  def serving: Receive = LoggingReceive {
    case CappuccinoRequest => {
      sender ! Bill(350)
      cappuccinoCount += 1
      log.info(s"Preparing cappuccino #${cappuccinoCount}")
    }
    case EspressoRequest => {
      sender ! Bill(250)
      espressoCount += 1
      log.info(s"Preparing espresso #${espressoCount}")
    }
    case GetServedCoffeeRequests => sender ! ServedCoffeeRequests(espressoCount, cappuccinoCount)
    case Lunch => context.become(lunchBreak)
    case CloseShop => context.stop(self)
  }

  def lunchBreak: Receive = LoggingReceive {
    case Work => context.become(serving)
    case _ => sender ! LunchBreak
  }

  override def receive: Receive = serving
}
