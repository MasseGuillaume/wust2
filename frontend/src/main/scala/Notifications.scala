package wust.frontend

import org.scalajs.dom.window
import org.scalajs.dom.experimental.{Notification, NotificationOptions}
import scalajs.js
import org.scalajs.dom._
import scalajs.js.JSConverters._

object Notifications extends Notifications

class Notifications {
  // https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API
  def notificationsGranted = Notification.permission == "granted"
  def notificationsDenied = Notification.permission == "denied"

  def notify(title: String, body: Option[String] = None, onclick: Notification => Any = _ => ()) {
    def fire() {
      val n = new Notification(title, NotificationOptions(body = body.orUndefined))
      n.addEventListener[Event]("click", { (event: Event) => onclick(event.target.asInstanceOf[Notification]) })
    }
    if (notificationsDenied) {
    } else if (notificationsGranted) {
      fire()
    } else {
      Notification.requestPermission { (permission: String) =>
        if (permission == "granted") {
          fire()
        }
      }
    }
  }
}
