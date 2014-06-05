package supervision

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import com.typesafe.config.ConfigFactory
import supervision.SupervisionSample.{Supervisor2, Child, Supervisor}

class SupervisionSampleSpec extends TestKit(ActorSystem("SupervisionSampleSystem", TestKitUsageSpec.config))
with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  "A supervisor" must {
    "apply the chosen strategy for its child" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[Child]
      val child = expectMsgType[ActorRef]

      child ! 42 // set state to 42
      child ! "get"
      expectMsg(42)

      child ! new ArithmeticException // crash it
      child ! "get"
      expectMsg(42)

      child ! new NullPointerException // crash it harder
      child ! "get"
      expectMsg(0) // state lost

      watch(child) // have testActor watch “child”
      child ! new IllegalArgumentException // break it
      expectTerminated(child)

      supervisor ! Props[Child] // create new child
      val child2 = expectMsgType[ActorRef]

      watch(child2)
      child2 ! "get" // verify it is alive
      expectMsg(0)

      child2 ! new Exception("CRASH") // escalate failure
      expectTerminated(child2)
    }
  }

  "A supervisor" must {
    "apply the chosen strategy for its child - not stop children" in {
      val supervisor2 = system.actorOf(Props[Supervisor2], "supervisor2") // should not stop children

      supervisor2 ! Props[Child] // create new child
      val child3 = expectMsgType[ActorRef]

      child3 ! 23
      child3 ! "get"
      expectMsg(23)

      child3 ! new Exception("CRASH")
      child3 ! "get"
      expectMsg(0)
    }
  }
}

object TestKitUsageSpec {
  // Define your test specific configuration here
  val config = ConfigFactory.parseString( """
    akka.loglevel = "DEBUG"
    akka.actor.debug {
    receive = on
    lifecycle = on
  }""")
}
