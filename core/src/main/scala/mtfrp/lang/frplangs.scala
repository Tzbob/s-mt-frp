package mtfrp.lang

import scala.js.language._
import scala.js.language.dom.Browser
import spray.json.DefaultJsonProtocol

trait MtFrpLib
  extends ClientFRPLib
  with ServerFRPLib
  with ReplicationFRPLib

trait MtFrpProg
  extends MtFrpLib
  with FrpExtensions
  with Browser
  with Adts
  with DocumentOpsExtended
  with DefaultJsonProtocol
  with HtmlNodeLib
  with JS {
  def main: ClientDiscreteBehavior[Html]
}

