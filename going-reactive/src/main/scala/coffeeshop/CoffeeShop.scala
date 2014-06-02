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

class Barista extends Actor {
    // TODO
    override def receive: Receive = ???
}
