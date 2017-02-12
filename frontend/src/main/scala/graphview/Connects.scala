package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.d3v4._
import org.scalajs.dom
import mhtml._

import vectory._
import util.collectionHelpers._

class ConnectionLineSelection(
  container: Selection[dom.EventTarget],
  rxPosts: RxPosts
)
  extends RxDataSelection[SimConnects](container, "line", rxPosts.rxSimConnects, keyFunction = Some((p: SimConnects) => p.id)) {

  override def enter(line: Selection[SimConnects]) {
    line
      .style("stroke", "#8F8F8F")
  }

  override def drawCall(line: Selection[SimConnects]) {
    line
      .attr("x1", (e: SimConnects) => e.source.x)
      .attr("y1", (e: SimConnects) => e.source.y)
      .attr("x2", (e: SimConnects) => e.target.x)
      .attr("y2", (e: SimConnects) => e.target.y)
  }
}

class ConnectionElementSelection(container: Selection[dom.EventTarget], rxPosts: RxPosts)
  extends RxDataSelection[SimConnects](container, "div", rxPosts.rxSimConnects, keyFunction = Some((p: SimConnects) => p.id)) {

  override def enter(element: Selection[SimConnects]) {
    element
      .style("position", "absolute")
      .style("font-size", "20px")
      .style("margin-left", "-0.5ex")
      .style("margin-top", "-0.5em")
      .text("\u00d7")
      .style("pointer-events", "auto") // reenable
      .style("cursor", "pointer")
      .on("click", { (e: SimConnects) =>
        import autowire._
        import boopickle.Default._

        Client.api.deleteConnection(e.id).call()
      })

  }

  override def drawCall(element: Selection[SimConnects]) {
    element
      .style("left", (e: SimConnects) => s"${e.x.get}px")
      .style("top", (e: SimConnects) => s"${e.y.get}px")

  }
}
