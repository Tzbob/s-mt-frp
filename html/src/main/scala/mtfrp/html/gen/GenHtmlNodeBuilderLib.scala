package mtfrp.html.gen

import mtfrp.gen.GenClientFRPLib
import scala.js.gen.js.GenJSMaps
import scala.js.gen.js.dom.GenEventOps
import scala.js.gen.js.GenJS
import mtfrp.exp.EventSourcesExp
import mtfrp.html.exp.HtmlNodeBuilderLibExp
import scala.js.gen.js.dom.GenElementOps
import scala.js.gen.QuoteGen

trait GenHtmlNodeBuilderLib extends GenClientFRPLib with GenEventOps with GenJSMaps with GenElementOps with QuoteGen {
  val IR: HtmlNodeBuilderLibExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case MkText(str) =>
      emitValDef(sym, s"new MTFRP.VText(${quote(str)})")
    case MkNode(tag, props, children) =>
      emitValDef(sym, q"new MTFRP.VNode($tag, $props, $children)")
    case CreateElem(vnode) =>
      emitValDef(sym, q"MTFRP.createElement($vnode)")
    case Diff(prev, current) =>
      emitValDef(sym, q"MTFRP.diff($prev, $current)")
    case Patch(root, patch) =>
      emitValDef(sym, q"MTFRP.patch($root, $patch)")
    case _ => super.emitNode(sym, rhs)
  }
}
