package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom
import org.scalajs.dom.{Event, document}
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}
import scala.scalajs.js.timers.setTimeout
import rx._
import wust.frontend._
import wust.ids._
import wust.graph._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try
import scalatags.JsDom.all._
import scalatags.rx.all._
import collection.breakOut
import wust.util.EventTracker.sendEvent

object AddPostForm {
  val inputfield = Elements.textareaWithEnterSubmit(rows := 3, cols := 80, width := "100%").render

  def editLabel(graph: Graph, editedPostId: WriteVar[Option[PostId]], postId: PostId) = {
    div(
      Views.parents(postId, graph),
      Views.post(graph.postsById(postId)),
      "Edit Post", button("cancel", onclick := { (_: Event) => editedPostId() = None; inputfield.value = "" })
    )
  }

  def responseLabel(graph: Graph, postId: PostId) = {
    div(
      width := "10em",
      "Responding to:",
      Views.post(graph.postsById(postId)),
      Views.parents(postId, graph)
    )
  }

  val sendButton = input(`type` := "submit", value := "send")

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state.{displayGraph => rxDisplayGraph, editedPostId => rxEditedPostId, mode => rxMode}

    rxMode.foreach { mode =>
      mode match {
        case EditMode(postId) => inputfield.value = rxDisplayGraph.now.graph.postsById(postId).title
        case _                =>
      }
      inputfield.focus()
    }

    def label(mode: InteractionMode, graph: Graph) = mode match {
      case EditMode(postId)  => editLabel(graph, rxEditedPostId, postId)
      case FocusMode(postId) => responseLabel(graph, postId)
      case _                 => span()
    }

    def action(text: String, selection: GraphSelection, groupIdOpt: Option[GroupId], graph: Graph, mode: InteractionMode): Future[Boolean] = mode match {
      case EditMode(postId) =>
        DevPrintln(s"\nUpdating Post $postId: $text")
        rxEditedPostId() = None
        val result = Client.api.updatePost(graph.postsById(postId).copy(title = text)).call()
        sendEvent("post", "update", "api")
        result
      case FocusMode(postId) =>
        DevPrintln(s"\nRepsonding to $postId: $text")
        val result = Client.api.respond(postId, text, selection, groupIdOpt).call().map(_.isDefined)
        sendEvent("post", "respond", "api")
        result
      case _ =>
        DevPrintln(s"\nCreating Post: $text")
        val result = Client.api.addPost(text, selection, groupIdOpt).call().map(_.isDefined)
        sendEvent("post", "create", "api")
        result
    }

    div(
      div(
        display.flex, justifyContent.spaceBetween,
        Rx { label(rxMode(), rxDisplayGraph().graph).render },
        form(
          width := "100%",
          display.flex,
          inputfield,
          sendButton,
          onsubmit := { () =>
            val text = inputfield.value
            if (text.trim.nonEmpty) {
              val success = action(text, state.graphSelection.now, state.selectedGroupId.now, rxDisplayGraph.now.graph, rxMode.now)
              success.foreach(if (_) inputfield.value = "")
            }
            false
          }
        )
      )
    )
  }
}
