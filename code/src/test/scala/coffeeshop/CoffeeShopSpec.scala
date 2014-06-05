package coffeeshop

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import coffeeshop.Barista._

class CoffeeShopSpec extends TestKit(ActorSystem("CoffeeShopSystem")) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  "A barista" must {
    "be able to serve coffee drink requests" in {
      val barista = system.actorOf(Props[Barista], "barista-tom")
      barista ! EspressoRequest
      expectMsg(Bill(250))
      barista ! CappuccinoRequest
      expectMsg(Bill(350))
    }
  }

  "A barista" must {
    "keep count of the number of prepared coffee drinks" in {
      val barista = system.actorOf(Props[Barista], "barista-john")
      barista ! EspressoRequest
      expectMsg(Bill(250))
      barista ! CappuccinoRequest
      expectMsg(Bill(350))
      barista ! EspressoRequest
      expectMsg(Bill(250))
      barista ! EspressoRequest
      expectMsg(Bill(250))
      barista ! CappuccinoRequest
      expectMsg(Bill(350))

      barista ! GetServedCoffeeRequests
      expectMsg(ServedCoffeeRequests(3, 2))
    }
  }

  "A barista" must {
    "not accept coffee requests while having lunch" in {
      val barista = system.actorOf(Props[Barista], "barista-peter")
      barista ! EspressoRequest
      expectMsg(Bill(250))

      barista ! Lunch
      barista ! EspressoRequest
      expectMsg(LunchBreak)

      barista ! Work
      barista ! EspressoRequest
      expectMsg(Bill(250))
    }
  }

  "A barista" must {
    "close for the day" in {
      val barista = system.actorOf(Props[Barista], "barista-sam")
      barista ! CloseShop

      barista ! EspressoRequest // går til dead letters
      expectNoMsg()
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
