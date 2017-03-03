package frontend

import graph._
import collection.breakOut

// views immutable, dass urls nicht kaputt gehen
// wust.space/view/$viewitd

trait Selector {
  import Selector._
  def intersect(that: Selector): Selector = new Intersect(this, that)
  def union(that: Selector): Selector = new Union(this, that)
  def apply(id: AtomId): Boolean
}
object Selector {
  // case class TitleMatch(regex: String) extends Selector
  case object Nothing extends Selector {
    override def apply(id: AtomId) = false
  }
  case object All extends Selector {
    override def apply(id: AtomId) = true
  }
  case class IdSet(set: Set[AtomId]) extends Selector {
    override def apply(id: AtomId) = set(id)
  }
  case class Union(a: Selector, b: Selector) extends Selector {
    def apply(id: AtomId) = a(id) || b(id)
  }
  case class Intersect(a: Selector, b: Selector) extends Selector {
    def apply(id: AtomId) = a(id) && b(id)
  }
}

case class View(
  collapsed: Selector = Selector.Nothing
) {
  def intersect(that: View) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: View) = copy(collapsed = this.collapsed union that.collapsed)
}

object View {
  def collapse(selector: Selector, graph: Graph): Graph = {
    //TODO: currently only top-level-parents can be collapsed
    val toCollapse: Iterable[AtomId] = graph.posts.keys.filter(selector.apply)
      //TODO: only the cycle should not be collapsed, but we should still collapse other children (not in the cycle)
      .filterNot(id => graph.involvedInCycle(id) && graph.transitiveParents(id).exists(selector.apply))

    val collapseChildren: Map[AtomId, Iterable[AtomId]] = toCollapse
      .map { collapsedId =>
        collapsedId -> graph.transitiveChildren(collapsedId)
      }(breakOut)

    val removableChildren: Set[AtomId] = collapseChildren.values.flatten
      .filter(id => graph.transitiveParents(id).toList.diff(collapseChildren.flatMap { case (k, v) => k :: v.toList }.toList).isEmpty)
      .toSet

    val removePosts: Map[AtomId, Iterable[AtomId]] = collapseChildren.mapValues(_.filter(removableChildren))

    val removeEdges: Map[AtomId, Set[AtomId]] = removePosts.values.flatten // TODO: use adjacency lists of graph directly
      .map { p =>
        p -> graph.incidentConnections(p)
      }(breakOut)

    def edgesToParents(addEdges: Map[AtomId, Connects], parent: AtomId, child: AtomId): Map[AtomId, Connects] = {
      val connectionMap = graph.connections ++ addEdges
      addEdges ++ removeEdges(child).map(connectionMap).map {
        case edge @ Connects(id, `child`, _) => id -> edge.copy(sourceId = parent)
        case edge @ Connects(id, _, `child`) => id -> edge.copy(targetId = parent)
      }
    }

    val addEdges = removePosts.toList.flatMap { case (parent, children) => children.map(child => parent -> child) }
      .foldLeft(Map.empty[AtomId, Connects])((edges, pc) => edgesToParents(edges, pc._1, pc._2))

    graph
      .removeConnections(removeEdges.values.flatten)
      .++(addEdges.values)
      .removePosts(removePosts.values.flatten)
  }

  def apply(view: View, graph: Graph): Graph = {
    val collapsed = collapse(view.collapsed, graph)
    collapsed
  }
}