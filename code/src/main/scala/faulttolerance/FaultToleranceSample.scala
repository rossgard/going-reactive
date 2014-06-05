package faulttolerance

import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._
import akka.event.LoggingReceive
import faulttolerance.Worker.Start
import com.typesafe.config.ConfigFactory

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("FaultToleranceSample", MainConfig.config)

    val listener = system.actorOf(Props[Listener], name = "listener")
    val worker = system.actorOf(Props[Worker], name = "worker")
    system.actorOf(Props(classOf[Terminator], listener), "terminator")
    // Sender is listener, not self
    worker.tell(Start, sender = listener)
  }

  class Terminator(ref: ActorRef) extends Actor with ActorLogging {
    context watch ref
    def receive = {
      case Terminated(_) =>
        log.info("{} has terminated, shutting down system", ref.path)
        context.system.shutdown()
    }
  }

  object MainConfig {
    val config = ConfigFactory.parseString( """
    akka.loglevel = "DEBUG"
    akka.actor.debug {
    receive = on
    lifecycle = on
  }""")
  }
}

/**
 * Lytter på fremdrift fra worker og stopper når nok arbeid er utført.
 */
class Listener extends Actor with ActorLogging {
  import Worker._

  context.setReceiveTimeout(15 seconds)

  def receive = {
    case Progress(percent) =>
      log.info("Current progress: {} %", percent)
      if (percent >= 100.0) {
        log.info("That’s all, shutting down")
        context.stop(self)
      }
    case ReceiveTimeout =>
      // Ingen fremdrift innen 15 sec, ServiceUnavailable
      log.error("Shutting down due to unavailable service")
      context.stop(self)
  }
}

object Worker {
  case object Start
  case object Do
  case class Progress(percent: Double)
}

/**
 * Worker utfører arbeid ved mottak av 'Start'-melding..
 * Worker vil fortløpende sende notifikasjoner til avsender av 'Start' og informere om fremdrift ('Progress').
 * Worker er supervisor for CounterService.
 */
class Worker extends Actor with ActorLogging {
  import Worker._
  import CounterService._

  override val supervisorStrategy = OneForOneStrategy() {
    case _: CounterService.ServiceUnavailable => Stop
  }

  var progressListener: Option[ActorRef] = None
  val counterService = context.actorOf(Props[CounterService], name = "counter")
  val totalCount = 51

  // Bruk actors Dispatcher som ExecutionContext
  import context.dispatcher

  def receive = LoggingReceive {
    case Start if progressListener.isEmpty =>
      progressListener = Some(sender)
      context.system.scheduler.schedule(Duration.Zero, 1 second, self, Do)
    case Do =>
      counterService ! Increment(1)
      counterService ! Increment(1)
      counterService ! Increment(1)
      counterService ! GetCurrentCount
    case CurrentCount(_, count) => {
      progressListener.get ! Progress(100.0 * count / totalCount)
    }
  }
}

object CounterService {
  case class Increment(n: Int)
  case object GetCurrentCount
  case class CurrentCount(key: String, count: Long)
  class ServiceUnavailable(msg: String) extends RuntimeException(msg)

  private case object Reconnect

}

/**
 * Verdi som mottas i 'Increment'-meldinger sendes til en persistent teller.
 * Svarer med CurrentCount ved forespørsler av type 'CurrentCount'.
 * Er Supervisor for Storage og Counter.
 */
class CounterService extends Actor {

  import CounterService._
  import Counter._
  import Storage._

  // Restart storage ved StorageException.
  // Storage stopped ved 3 restarter innen 5 sekund.
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 5 seconds) {
    case _: Storage.StorageException => Restart
  }

  val key = self.path.name
  var storage: Option[ActorRef] = None
  var counter: Option[ActorRef] = None
  var backlog = IndexedSeq.empty[(ActorRef, Any)]
  val MaxBacklog = 10000

  // Actors Dispatcher som ExecutionContext
  import context.dispatcher

  override def preStart() {
    initStorage()
  }

  /**
   * Storage restartes ved feil, men stoppes hvis den fortsatt feiler etter 3 restarter.
   * Når storage er stoppet, så prøves en 'Reconnect' etter en hvis tid.
   * Vi holder også øye med barnenoden slik at vi får Terminated meldinger når barnenoden stoppes.
   */
  def initStorage() {
    storage = Some(context.watch(context.actorOf(Props[Storage], name = "storage")))

    // Gi beskjed til telleren (hvis den finnes) om persistent lager.
    counter foreach {
      _ ! UseStorage(storage)
    }

    // Vi trenger sist lagrede verdi før vi kan begynne å telle (svarer med Entry(...))
    storage.get ! Get(key)
  }

  def receive = LoggingReceive {
    case Entry(k, v) if k == key && counter == None =>
      // Svar fra Storage med sist lagrede verdi, kan opprette Counter
      val c = context.actorOf(Props(classOf[Counter], key, v))
      counter = Some(c)
      // Gi beskjed til Counter om persistent storage
      c ! UseStorage(storage)
      // send buffret backlog til teller
      for ((replyTo, msg) <- backlog) c.tell(msg, sender = replyTo)
      backlog = IndexedSeq.empty

    case msg@Increment(n) => forwardOrPlaceInBacklog(msg)

    case msg@GetCurrentCount => forwardOrPlaceInBacklog(msg)

    case Terminated(actorRef) if Some(actorRef) == storage =>
      // Lager stoppes etter 3 restarter. Vi får Terminated fordi vi holder øye med noden (watch).
      storage = None
      // Gi beskjed til tellerom  at vi ikke har persistent lager for øyeblikket
      counter foreach {
        _ ! UseStorage(None)
      }
      // Prøv å koble til på nytt etter en stund
      context.system.scheduler.scheduleOnce(10 seconds, self, Reconnect)
    case Reconnect =>
      // Koble til på nytt
      initStorage()
  }

  def forwardOrPlaceInBacklog(msg: Any) {
    // Vi trenger sist lagrede verdi fra persistent lager før vi kan delegere til teller.
    counter match {
      case Some(c) => c forward msg
      case None =>
        if (backlog.size >= MaxBacklog)
          throw new ServiceUnavailable("CounterService not available, lack of initial value")
        backlog :+= (sender -> msg) // mangler teller, putt mld i backlog
    }
  }
}

object Counter {
  case class UseStorage(storage: Option[ActorRef])
}

/**
 * Teller-variabel som sender gjeldende teller til persistent lager hvis lageret er tilgjengelig.
 * @param key nøkkel
 * @param initialValue startverdi for nøkkel
 */
class Counter(key: String, initialValue: Long) extends Actor {
  import Counter._
  import CounterService._
  import Storage._

  var count = initialValue
  var storage: Option[ActorRef] = None

  def receive = LoggingReceive {
    case UseStorage(s) =>
      storage = s
      storeCount()
    case Increment(n) =>
      count += n
      storeCount()
    case GetCurrentCount =>
      sender ! CurrentCount(key, count)
  }

  def storeCount() {
    // Deleger farlig arbeid, vi kan fortsette uten lager
    storage foreach {
      _ ! Store(Entry(key, count))
    }
  }
}

object Storage {
  case class Store(entry: Entry)
  case class Get(key: String)
  case class Entry(key: String, value: Long)
  class StorageException(msg: String) extends RuntimeException(msg)
}

/**
 * Lagrer nøkkel/verdi par til DB ved mottak av 'Store'-melding.
 * Svarer med siste verdi ved mottak av 'Get'-melding.
 * @throws StorageException hvis data er ute av sekvens
 */
class Storage extends Actor {
  import Storage._

  val db = DummyDB

  def receive = LoggingReceive {
    case Store(Entry(key, count)) => db.save(key, count)
    case Get(key) => sender ! Entry(key, db.load(key).getOrElse(0L))
  }
}

/**
 * DummyDB som simulerer feil.
 */
object DummyDB {

  import Storage.StorageException

  private var db = Map[String, Long]()

  @throws(classOf[StorageException])
  def save(key: String, value: Long): Unit = synchronized {
    if (11 <= value && value <= 14)
      throw new StorageException("Simulated store failure " + value)
    db += (key -> value)
  }

  @throws(classOf[StorageException])
  def load(key: String): Option[Long] = synchronized {
    db.get(key)
  }
}


