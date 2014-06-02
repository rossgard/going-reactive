package faulttolerance

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ReceiveTimeout, Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import faulttolerance.Worker.{Progress, Start}
import com.typesafe.config.ConfigFactory

class FaultToleranceSpec extends TestKit(ActorSystem("FaultTolerantSystem", TestKitUsageSpec.config)) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  val listener = system.actorOf(Props[Listener], "listener")
  val worker = system.actorOf(Props[Worker], "worker")

  "Listener actor" must {
    "shut down on ReceiveTimeOut" in {
      watch(listener)
      listener ! ReceiveTimeout
      expectTerminated(listener, 3 second)
    }
  }

  "Listener actor" must {
    "shut down when progress reaches 100 %" in {
      watch(listener)
      listener ! Progress(100)
      expectTerminated(listener, 3 second)
    }
  }

  "Worker actor" must {
    "report progress after started" in {
      worker ! Start
      expectMsgClass(classOf[Progress])
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}

object TestKitUsageSpec {
  val config = ConfigFactory.parseString( """
    akka.loglevel = "DEBUG"
    akka.actor.debug {
    receive = on
    lifecycle = on
  }""")
}