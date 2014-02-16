package smtfrp.examples

import mtfrp.lang.MtFrpProg
import spray.json.DefaultJsonProtocol

import collection._

trait GuestbookProg extends MtFrpProg {
  import DefaultJsonProtocol._

  case class Entry(name: String, text: String) extends Adt
  val ClientEntry = adt[Entry]
  implicit def entryOps(p: Rep[Entry]) = adtOps(p)
  implicit val entryFormat = jsonFormat2(Entry)

  def main: ClientSignal[Element] = book.toClient map template

  def template(data: Rep[List[Entry]]): Rep[Element] = {
    val entryEls =
      for (entry <- data)
        yield el('div)(entry.name, " says ", entry.text)

    el('div)(
      el('h1)("Echo prog"),
      entryEls,
      el('div)(name, text, send)
    )
  }

  lazy val book: ServerSignal[List[Entry]] =
    input.toServer.fhold(immutable.List.empty[Entry]) { (acc, tup) =>
      tup._2 :: acc
    }

  lazy val input: ClientEvent[Entry] = {
    val combined = name.values.combine(text.values)(ClientEntry(_, _))
    val signal = combined hold ClientEntry("", "")
    signal sampledBy send.toStream(Click)
  }

  lazy val name: Rep[Input] = createInput("text")
  lazy val text: Rep[Input] = createInput("text")
  lazy val send: Rep[Button] = {
    val send = document createElement ButtonTag
    send.setInnerHTML("Send")
    send
  }

  def createInput(tp: Rep[String]): Rep[Input] = {
    val el = document createElement InputTag
    el.setAttribute("type", tp)
    el
  }
}