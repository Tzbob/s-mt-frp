package smtfrp.examples

import spray.json.DefaultJsonProtocol
import mtfrp.lang.MtFrpProg
import mtfrp.lang.Client
import collection.{ immutable => i }
import frp.core.DeltaApplicator

trait BasicChatProg extends MtFrpProg with EasyHTML {
  import DefaultJsonProtocol._

  case class Entry(name: String, msg: String)
    extends Adt
  val EntryRep: (Rep[String], Rep[String]) => Rep[Entry] =
    adt[Entry]
  implicit val itemFormat = jsonFormat2(Entry)

  lazy val name: Rep[Input] = text("Name")
  lazy val msg: Rep[Input] = text("Message")
  lazy val send: Rep[Button] = button("Send")

  lazy val submit: ClientEvent[Entry] = {
    val nameV: ClientBehavior[String] = name.values
    val msgV: ClientBehavior[String] = msg.values
    val entry = nameV.combine(msgV) { EntryRep(_, _) }

    val clicks: ClientEvent[MouseEvent] = send.toStream(Click)
    entry.sampledBy(clicks)
  }
  lazy val serverSubmit: ServerEvent[Entry] = submit.toServerAnon

  def serverListPrepender[A] = new ServerDeltaApplicator[List[A], A] {
    def apply(acc: List[A], delta: A): List[A] = delta :: acc
  }

  def clientListPrepender[A: Manifest] = new ClientDeltaApplicator[List[A], A] {
    def apply(acc: Rep[List[A]], delta: Rep[A]): Rep[List[A]] = delta :: acc
  }

  lazy val chat: ServerIncBehavior[Entry, List[Entry]] =
    serverSubmit.incFold(i.List.empty[Entry])(serverListPrepender)

  def template(view: Rep[List[Entry]]): Rep[Element] = {
    implicit def itemOps(p: Rep[Entry]) = adtOps(p)
    def template(post: Rep[Entry]) = el('li)(post.name, " says ", post.msg)
    val contents = view.map(template)
    el('div)(
      el('h1)(
        "Multi-tier Chat"), el('hr)(),
      el('div)(name, msg, send),
      el('h3)("Public"), el('ol)(contents), el('hr)())
  }

  lazy val main: ClientBehavior[Element] =
    chat.toAllClients(clientListPrepender).map(template)

}