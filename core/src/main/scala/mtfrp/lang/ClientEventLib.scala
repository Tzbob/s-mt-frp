package mtfrp.lang

import java.net.URLEncoder
import java.util.UUID
import scala.js.language.{ JS, JSLiteral }
import akka.actor.actorRef2Scala
import reactive.{ EventStream, Observing }
import spray.http.{ CacheDirectives, ChunkedResponseStart, HttpHeaders, HttpResponse, MediaType, MessageChunk }
import spray.http.HttpEntity.apply
import spray.json._
import spray.routing.{ Directives, RequestContext, Route }
import spray.routing.Directives._
import scala.js.language.dom.EventOps

trait ClientEventLib extends JSJsonReaderLib with BaconLib with EventSources
    with JS with JSLiteral with EventOps with DelayedEval {
  self: ServerEventLib with ClientBehaviorLib =>

  private def initEventSource[T: JSJsonReader: Manifest](bus: Rep[Bus[T]], url: String): Rep[EventSource] = {
    val source = EventSource(includeClientIdParam(url))
    source.onmessage = fun { ev: Rep[Dataliteral] =>
      bus.push(implicitly[JSJsonReader[T]] read ev.data)
    }
    source
  }

  private def initRoute[T: JsonWriter](url: String, stream: EventStream[Client => Option[T]])(implicit observing: Observing): Route =
    path(url) {
      get {
        parameter('id) { id =>
          val client = Client(id)
          respondWithMediaType(MediaType.custom("text/event-stream")) {
            ctx: RequestContext =>
              ctx.responder ! ChunkedResponseStart(HttpResponse(
                headers = HttpHeaders.`Cache-Control`(CacheDirectives.`no-cache`) :: Nil,
                entity = ":" + (" " * 2049) + "\n" // 2k padding for IE polyfill (yaffle)
              ))
              stream foreach { fun =>
                for (data <- fun(client)) {
                  ctx.responder ! MessageChunk(s"data:${data.toJson.compactPrint}\n\n")
                }
              }
          }
        }
      }
    }

  private[mtfrp] object ClientEvent {
    import Directives._

    def apply[T: Manifest](stream: Rep[BaconStream[T]]): ClientEvent[T] =
      new ClientEvent(None, stream, None)

    def apply[T: Manifest](
      route: Option[Route],
      stream: Rep[BaconStream[T]],
      observing: Option[Observing]): ClientEvent[T] =
      new ClientEvent(route, stream, observing)

    def apply[T: JsonWriter: JSJsonReader: Manifest](serverStream: ServerEvent[Client => Option[T]]) = {
      val genUrl = URLEncoder encode (UUID.randomUUID.toString, "UTF-8")
      val bus = Bus[T]()
      initEventSource(bus, genUrl)

      implicit val observing = serverStream.observing getOrElse new Observing {}
      val currentRoute = initRoute(genUrl, serverStream.stream)
      val route = serverStream.route.map(_ ~ currentRoute) getOrElse currentRoute

      new ClientEvent(Some(route), bus, Some(observing))
    }

  }

  implicit class ReactiveToServer[T: JsonReader: JSJsonWriter: Manifest](evt: ClientEvent[T]) {
    def toServerAnon: ServerEvent[T] = ServerEvent(evt).map { tuple => tuple._2 }
    def toServer: ServerEvent[(Client, T)] = ServerEvent(evt)
  }

  class ClientEvent[+T: Manifest] private (
      val route: Option[Route],
      val rep: Rep[BaconStream[T]],
      val observing: Option[Observing]) {

    private[this] def copy[A: Manifest](
      route: Option[Route] = this.route,
      rep: Rep[BaconStream[A]] = this.rep,
      observing: Option[Observing] = this.observing): ClientEvent[A] =
      new ClientEvent(route, rep, observing)

    def map[A: Manifest](modifier: Rep[T] => Rep[A]): ClientEvent[A] = {
      val rep = this.rep.map(fun(modifier))
      this.copy(rep = rep)
    }

    def merge[A >: T: Manifest](stream: ClientEvent[A]): ClientEvent[A] =
      this.copy(rep = rep.merge(stream.rep))

    def filter(pred: Rep[T] => Rep[Boolean]): ClientEvent[T] =
      this.copy(rep = rep.filter(fun(pred)))

    def fold[A: Manifest](start: Rep[A])(stepper: (Rep[A], Rep[T]) => Rep[A]): ClientEvent[A] =
      this.copy(rep = rep.fold(start)(fun(stepper)))

    def hold[U >: T: Manifest](initial: Rep[U]): ClientBehavior[U] =
      ClientBehavior(initial, this)

    def fhold[A: Manifest](start: Rep[A])(stepper: (Rep[A], Rep[T]) => Rep[A]): ClientBehavior[A] =
      this.fold(start)(stepper) hold start
  }
}
