package frontend.views.graphview

import scalajs.js
import org.scalajs.dom
import js.JSConverters._
import org.scalajs.d3v4._
import rx._
import scalatags.rx.all._
import scalatags.JsDom.all._
import math._
import util.Pipe

import frontend.GlobalState
import graph._
import frontend.Color._

case class MenuAction(name: String, action: (SimPost, Simulation[SimPost]) => Unit)
case class DropAction(name: String, action: (SimPost, SimPost) => Unit)

object KeyImplicits {
  implicit val SimPostWithKey = new WithKey[SimPost](_.id)
  implicit val SimConnectsWithKey = new WithKey[SimConnects](_.id)
  implicit val ContainmentClusterWithKey = new WithKey[ContainmentCluster](_.id)
}

class GraphView(state: GlobalState, element: dom.html.Element)(implicit ctx: Ctx.Owner) {
  val graphState = new GraphState(state)
  val d3State = new D3State
  val postDrag = new PostDrag(graphState, d3State, onPostDrag)
  import state.{graph => rxGraph, _}
  import graphState._

  // prepare containers where we will append elements depending on the data
  // order is important
  import KeyImplicits._
  val container = d3.select(element)
  val svg = container.append("svg")
  val containmentHullSelection = SelectData.rx(ContainmentHullSelection, rxContainmentCluster)(svg.append("g"))
  val connectionLineSelection = SelectData.rx(ConnectionLineSelection, rxSimConnects)(svg.append("g"))

  val html = container.append("div")
  val connectionElementSelection = SelectData.rx(ConnectionElementSelection, rxSimConnects)(html.append("div"))
  val postSelection = SelectData.rx(new PostSelection(graphState, postDrag), rxSimPosts)(html.append("div"))
  val draggingPostSelection = SelectData.rxDraw(DraggingPostSelection, postDrag.draggingPosts)(html.append("div")) //TODO: place above ring menu?

  val menuSvg = container.append("svg")
  val postMenuLayer = menuSvg.append("g")
  val postMenuSelection = SelectData.rxDraw(new PostMenuSelection(graphState, d3State), rxFocusedSimPost.map(_.toJSArray))(postMenuLayer.append("g"))
  val dropMenuLayer = menuSvg.append("g")
  val dropMenuSelection = SelectData.rxDraw(DropMenuSelection, postDrag.closestPosts)(dropMenuLayer.append("g"))

  initContainerDimensionsAndPositions()
  initEvents()
  rxGraph.foreach(update) // TODO

  Rx { rxSimPosts(); rxSimConnects(); rxSimContains() }.triggerLater {
    val simPosts = rxSimPosts.now
    val simConnects = rxSimConnects.now
    val simContains = rxSimContains.now
    println("    updating graph simulation")
    d3State.simulation.nodes(simPosts)
    d3State.forces.connection.links(simConnects)
    d3State.forces.containment.links(simContains)

    //TODO: this can be removed after implementing link force which supports hyperedges
    // d3State.forces.connection.strength { (e: SimConnects) =>
    //   val targetDeg = e.target match {
    //     case p: SimPost => graph.fullDegree(p.post.id)
    //     case _: SimConnects => 2
    //   }
    //   1.0 / math.min(graph.fullDegree(e.source.post.id), targetDeg)
    // }

    // d3State.forces.containment.strength { (e: SimContains) =>
    //   1.0 / math.min(graph.fullDegree(e.source.post.id), graph.fullDegree(e.target.post.id))
    // }
  }

  private def onPostDrag() {
    draggingPostSelection.draw()
  }

  private def initEvents(): Unit = {
    svg.call(d3.zoom().on("zoom", zoomed _))
    svg.on("click", () => focusedPostId := None)
    d3State.simulation.on("tick", draw _)
    //TODO: currently produces NaNs: rxSimConnects.foreach { data => d3State.forces.connection.links = data }
  }

  private def zoomed() {
    import d3State._
    transform = d3.event.asInstanceOf[ZoomEvent].transform
    svg.selectAll("g").attr("transform", transform.toString)
    html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
    postMenuLayer.attr("transform", transform.toString)
    dropMenuLayer.attr("transform", transform.toString)
  }

  private def draw() {
    postSelection.draw()
    postMenuSelection.draw()
    connectionLineSelection.draw()
    connectionElementSelection.draw()
    containmentHullSelection.draw()
  }

  private def initContainerDimensionsAndPositions() {
    container
      .style("position", "absolute")
      .style("top", "0")
      .style("left", "0")
      .style("z-index", "-1")
      .style("width", "100%")
      .style("height", "100%")
      .style("overflow", "hidden")

    svg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")

    html
      .style("position", "absolute")
      .style("pointer-events", "none") // pass through to svg (e.g. zoom)
      .style("transform-origin", "top left") // same as svg default
      .style("width", "100%")
      .style("height", "100%")

    menuSvg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")
      .style("pointer-events", "none")
  }

  private def update(newGraph: Graph) {
    import d3State._, graphState._

    simulation.alpha(1).restart()
  }
}

object GraphView {
  def component(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div().render ||> (new GraphView(state, _))
  }
}
