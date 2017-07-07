package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.algorithm.{ TreeContext, Tree, redundantSpanningTree }
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag
import scala.math.Ordering

import org.scalajs.dom.{ window, document, console }
import org.scalajs.dom.raw.{ Text, Element, HTMLElement, Node }
import scalatags.JsDom.all._
import scalatags.JsDom.TypedTag
import scala.scalajs.js
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{ Event, KeyboardEvent }

object TreeView {
  import Elements._

  //TODO better?
  private var focusedPostId: Option[PostId] = None
  private var treeContext: TreeContext[Post] = new TreeContext()

  implicit val postOrdering = new Ordering[Post] {
    def compare(a: Post, b: Post) = Tag.unwrap(a.id) compare Tag.unwrap(b.id)
  }

  // preserves newlines and white spaces: white-space: pre
  def textfield = div(contenteditable := "true", style := "white-space: pre", width := "80ex")

  def movePoint(postId: PostId) = div(
    paddingLeft := "5px", paddingRight := "5px", cursor := "pointer",
    span("☰"),
    ondragstart := { (e: Event) =>
      console.log("dend", e)
    },
    ondragend := { (e: Event) =>
      console.log("dstart", e)
    },
    ondrop := { (e: Event) =>
      console.log("drobbing", e)
    }
  )

  def bulletPoint(state: GlobalState, postId: PostId) = div(
    paddingLeft := "5px", paddingRight := "5px", cursor := "pointer",
    "•",
    onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
  )

  def collapseButton(state: GlobalState, postId: PostId)(implicit ctx: Ctx.Owner) = div(
    paddingLeft := "5px", paddingRight := "5px", cursor := "pointer",
    state.collapsedPostIds.map(ids => if (ids(postId)) "+" else "-"),
    onclick := { () => state.collapsedPostIds() = state.collapsedPostIds.now toggle postId }
  )

  def deleteButton(state: GlobalState, postId: PostId) = div(
    paddingLeft := "5px", paddingRight := "5px", cursor := "pointer",
    "✖",
    onclick := { () =>
      val containments = GraphSelection.toContainments(state.graphSelection.now, postId)
      state.persistence.addChangesEnriched(delPosts = Seq(postId), delContainments = containments)
    }
  )

  def nextInParent(elem: HTMLElement, next: HTMLElement => Option[HTMLElement]): Option[HTMLElement] = {
    val sibling = next(elem)
    sibling orElse {
      val parent = Option(elem.parentElement)
      parent.flatMap(nextInParent(_, next))
    }
  }

  def findNextTextfield(elem: HTMLElement, backwards: Boolean): Option[HTMLElement] = {
    val queried = elem.querySelectorAll("""div[contenteditable="true"]:not([disabled])""")

    if (queried.length <= 1) None
    else {
      var foundIdx: Option[Int] = None;
      for (i <- 0 until queried.length) {
        val e = queried(i).asInstanceOf[HTMLElement]
        if (e == document.activeElement)
          foundIdx = Some(i);
      }

      foundIdx.flatMap { foundIdx =>
        val offset = if (backwards) -1 else 1
        val nextIdx = (foundIdx + offset) match {
          case x if x < 0              => queried.length - 1
          case x if x > queried.length => 0
          case x                       => x
        }
        queried(nextIdx).asInstanceOf[js.UndefOr[HTMLElement]].toOption
      }
    }
  }

  def focusUp(elem: HTMLElement) = {
    nextInParent(elem.parentElement.parentElement.parentElement, findNextTextfield(_, backwards = true)).foreach(focusAndSetCursor _)
  }
  def focusDown(elem: HTMLElement) = {
    nextInParent(elem.parentElement.parentElement.parentElement, findNextTextfield(_, backwards = false)).foreach(focusAndSetCursor _)
  }

  def textAroundCursorSelection(elem: HTMLElement) = {
    val cursorRange = window.getSelection.getRangeAt(0)
    val lhs = document.createRange()
    val rhs = document.createRange()
    lhs.setStartBefore(elem)
    lhs.setEnd(cursorRange.startContainer, cursorRange.startOffset)
    rhs.setStart(cursorRange.endContainer, cursorRange.endOffset)
    rhs.setEndAfter(elem)
    (lhs.toString, rhs.toString)
  }

  def focusAndSetCursor(elem: HTMLElement) {
    try {
      elem.focus()
      val s = window.getSelection()
      val r = document.createRange()
      r.selectNodeContents(Option(elem.firstChild).getOrElse(elem))
      r.collapse(false) // false: collapse to end, true: collapse to start
      s.removeAllRanges()
      s.addRange(r)
    } catch { case _: Throwable => } // https://github.com/tmpvar/jsdom/issues/317
  }

  def handleKeyOnPost(state: GlobalState, tree: Tree[Post], event: KeyboardEvent) = {
    val post = tree.element
    val elem = event.target.asInstanceOf[HTMLElement]
    onKey(event) {
      case KeyCode.Enter if !event.shiftKey =>
        val (currPostText, newPostText) = textAroundCursorSelection(elem)
        val updatedPost = if (post.title != currPostText) Some(post.copy(title = currPostText)) else None
        val newPost = Post.newId(newPostText)
        //TODO: do not create empty post, create later when there is a title
        focusedPostId = Some(newPost.id)
        treeContext.parentMap.get(tree) match {
          case Some(parentTree) =>
            val newContainment = Containment(parentTree.element.id, newPost.id)
            state.persistence.addChangesEnriched(addPosts = Set(newPost), addContainments = Set(newContainment), updatePosts = updatedPost.toSet)
          case None =>
            val selection = state.graphSelection.now
            val containments = GraphSelection.toContainments(selection, newPost.id)
            state.persistence.addChangesEnriched(addPosts = Set(newPost), addContainments = containments, updatePosts = updatedPost.toSet)
        }
        false
      case KeyCode.Tab => event.shiftKey match {
        case false =>
          treeContext.previousMap.get(tree).foreach { previousTree =>
            val newContainment = Containment(previousTree.element.id, post.id)
            val delContainment = treeContext.parentMap.get(tree).map { parentTree =>
              Containment(parentTree.element.id, post.id)
            }
            focusedPostId = Some(post.id)
            state.persistence.addChanges(addContainments = Set(newContainment), delContainments = delContainment.toSet)
          }
          false
        case true =>
          for {
            parent <- treeContext.parentMap.get(tree)
            grandParent = treeContext.parentMap.get(parent)
          } {
            val newContainments = grandParent match {
              case Some(grandParent) => Set(Containment(grandParent.element.id, post.id))
              case None              => GraphSelection.toContainments(state.graphSelection.now, post.id)
            }
            val delContainment = Containment(parent.element.id, post.id)
            focusedPostId = Some(post.id)
            state.persistence.addChanges(addContainments = newContainments, delContainments = Set(delContainment))
          }
          false
      }
      case KeyCode.Up if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        if (sel.startOffset == sel.endOffset && !elem.textContent.take(sel.startOffset).contains('\n')) {
          focusedPostId = None
          focusUp(elem)
          false
        } else true
      case KeyCode.Down if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        if (sel.startOffset == sel.endOffset && !elem.textContent.drop(sel.endOffset).contains('\n')) {
          focusedPostId = None
          focusDown(elem)
          false
        } else true
      case KeyCode.Delete if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        val textElem = elem.firstChild.asInstanceOf[Text]
        if (sel.startOffset == textElem.length && sel.endOffset == textElem.length) {
          treeContext.nextMap.get(tree).map { nextTree =>
            val nextPost = nextTree.element
            val updatedPost = post.copy(title = post.title + " " + nextPost.title)
            focusedPostId = Some(post.id)
            state.persistence.addChanges(updatePosts = Set(updatedPost), delPosts = Set(nextPost.id))
            false
          }.getOrElse(true)
        } else true
      case KeyCode.Backspace if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        if (sel.startOffset == 0 && sel.endOffset == 0) {
          treeContext.previousMap.get(tree).map { previousTree =>
            val prevPost = previousTree.element
            val (_, remainingText) = textAroundCursorSelection(elem)
            val updatedPost = prevPost.copy(title = prevPost.title + " " + remainingText)
            focusedPostId = None
            focusUp(elem)
            state.persistence.addChanges(updatePosts = Set(updatedPost), delPosts = Set(post.id))
            false
          }.getOrElse(true)
        } else true
    }
  }

  def postItem(state: GlobalState, tree: Tree[Post])(implicit ctx: Ctx.Owner): Frag = {
    val post = tree.element
    val area = textfield(
      post.title,
      onfocus := { () =>
        if (focusedPostId.isEmpty)
          focusedPostId = Some(post.id)
      },
      onblur := { (event: Event) =>
        val elem = event.target.asInstanceOf[HTMLElement]
        if (post.title != elem.textContent) {
          val updatedPost = post.copy(title = elem.textContent)
          state.persistence.addChanges(updatePosts = Set(updatedPost))
        }
      },
      onkeydown := { (e: KeyboardEvent) => handleKeyOnPost(state, tree, e) }
    ).render

    //TODO: better?
    if (focusedPostId.map(_ == post.id).getOrElse(false)) {
      setTimeout(60) { focusAndSetCursor(area) }
    }

    div(
      display.flex,
      collapseButton(state, post.id),
      bulletPoint(state, post.id),
      area,
      movePoint(post.id),
      deleteButton(state, post.id)
    )
  }

  def newItem(state: GlobalState)(implicit ctx: Ctx.Owner): Frag = {
    val area = textfield(
      "",
      onblur := { (event: Event) =>
        val elem = event.target.asInstanceOf[HTMLElement]
        val text = elem.textContent
        if (text.nonEmpty) {
          val addPost = Post.newId(text)
          state.persistence.addChangesEnriched(addPosts = Set(addPost))
        }
      },
      onkeydown := { (event: KeyboardEvent) =>
        onKey(event) {
          case KeyCode.Enter if !event.shiftKey =>
            event.target.asInstanceOf[HTMLElement].blur()
            false
        }
      }
    ).render

    setTimeout(60) { focusAndSetCursor(area) }

    div(
      display.flex,
      "create new post: ",
      area
    )
  }

  def postTreeItem(state: GlobalState, tree: Tree[Post])(implicit ctx: Ctx.Owner) = {
    div(
      paddingLeft := "10px",
      postItem(state, tree),
    )
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val content = div(padding := "100px").render

    implicit val htmlableTree = new HtmlableElement[Tree[Post]] {
      def toId(t: Tree[Post]): String = Tag.unwrap(t.element.id)
      def toHtml(t: Tree[Post]): TypedTag[HTMLElement] = postTreeItem(state, t)
      def isEqual(t1: Tree[Post], t2: Tree[Post]): Boolean = t1.element.id == t2.element.id && t1.element.title == t2.element.title
    }

    val htmlUpdater = new UpdateHtml[Tree[Post]](content)

    state.displayGraphWithParents.foreach { dg =>
      import dg.graph
      val rootPosts = graph.posts
        .filter(p => graph.parents(p.id).isEmpty)
        .toList
        .sorted

      def postChildren(post: Post): Iterable[Post] = {
        graph.children(post.id).map(graph.postsById(_))
      }

      val trees = rootPosts.map(redundantSpanningTree[Post](_, postChildren _))

      //sideEffect: set treeContext
      treeContext = new TreeContext(trees: _*)

      //TODO:
      // val items = trees.map(postTreeItem(state, context, _))
      // val itemsOrNew = if (items.isEmpty) Seq(newItem(state)) else items

      //TODO
      // val childNodes = tree.children
      //   .sortBy(_.element)
      //   .map(postTreeItem(state, treeContext, _))
      //   .toList

      htmlUpdater.update(trees)
    }

    div(
      content
    )
  }
}

trait HtmlableElement[T] {
  def toId(t: T): String
  def toHtml(t: T): TypedTag[HTMLElement]
  def isEqual(t1: T, t2: T): Boolean
}

trait RenderHtml[T] {
  def update(elements: Seq[T])(implicit htmlable: HtmlableElement[T]): Unit
}

class RenderNestedHtml[T] extends RenderHtml[T] {
  def update(elements: Seq[T])(implicit htmlable: HtmlableElement[T]): Unit = {
  }
}

class UpdateHtml[T](baseHtml: Node) extends RenderHtml[T] {
  private var members = Map.empty[String, T]

  def update(elements: Seq[T])(implicit htmlable: HtmlableElement[T]): Unit = {
    import htmlable._

    val removedIds = members.keySet filterNot elements.map(toId).toSet
    removedIds.foreach { id =>
      val existingHtml = document.getElementById(id)
      baseHtml.removeChild(existingHtml)
    }

    //TODO sorting changed, replace

    elements.foreach { elem =>
      val id = toId(elem)
      members.get(id) match {
        case Some(prevElem) =>
          if (!isEqual(prevElem, elem)) {
            val existingHtml = document.getElementById(id)
            val newHtml = toHtml(elem)(attr("id") := id).render
            baseHtml.replaceChild(newHtml, existingHtml)
          }
        case None =>
          val newHtml = toHtml(elem)(attr("id") := id).render
          baseHtml.appendChild(newHtml)
      }
    }

    members = elements.by(toId)
  }
}
