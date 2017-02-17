package frontend.views.graphview

import frontend._

import graph._
import math._
import mhtml._

import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._
import autowire._
import boopickle.Default._
import com.outr.scribe._

object DropMenu {
  val dropActions = (
    DropAction("connect", "green", { (dropped: SimPost, target: SimPost) => Client.api.connect(dropped.id, target.id).call() }) ::
    DropAction("insert into", "blue", { (dropped: SimPost, target: SimPost) => Client.api.contain(target.id, dropped.id).call() }) ::
    DropAction("Merge", "red", { (dropped: SimPost, target: SimPost) => /*Client.api.merge(target.id, dropped.id).call()*/ }) ::
    Nil
  ).toArray
}

object DropMenuSelection extends DataSelection[SimPost] {
  val menuOuterRadius = 100.0
  val menuInnerRadius = 30.0
  val menuPaddingAngle = 2.0 * Pi / 200.0
  val menuCornerRadius = 2.0

  override val tag = "g"
  override def enter(menu: Selection[SimPost]) {
    val pie = d3.pie()
      .value(1)
      .padAngle(menuPaddingAngle)

    val arc = d3.arc()
      .innerRadius(menuInnerRadius)
      .outerRadius(menuOuterRadius)
      .cornerRadius(menuCornerRadius)

    val pieData = DropMenu.dropActions.toJSArray
    val ringMenuArc = menu.selectAll("path")
      .data(pie(pieData))
    val ringMenuLabels = menu.selectAll("text")
      .data(pie(pieData))

    ringMenuArc.enter()
      .append("path")
      .attr("d", (d: PieArcDatum[DropAction]) => arc(d))
      .attr("fill", "rgba(0,0,0,0.7)")
      .style("cursor", "pointer")
      .style("pointer-events", "all")

    ringMenuLabels.enter()
      .append("text")
      .text((d: PieArcDatum[DropAction]) => d.data.symbol)
      .attr("text-anchor", "middle")
      .attr("fill", "white")
      .attr("x", (d: PieArcDatum[DropAction]) => arc.centroid(d)(0))
      .attr("y", (d: PieArcDatum[DropAction]) => arc.centroid(d)(1))
  }

  override def draw(menu: Selection[SimPost]) {
    menu.attr("transform", (p: SimPost) => s"translate(${p.x}, ${p.y})")
  }
}