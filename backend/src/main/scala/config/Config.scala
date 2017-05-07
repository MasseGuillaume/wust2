package wust.backend.config

import autoconfig.config
import derive.derive
import wust.ids._

@derive((endpoint, username) => toString)
case class SmtpConfig(endpoint: String, username: String, password: String)
case class UsergroupConfig(publicId: GroupId)
case class AuthConfig(enableImplicit: Boolean, tokenLifetime: Long, secret: String) //TODO: tokenLifetime -> Duration
case class EmailConfig(fromAddress: String, smtp: SmtpConfig)

@config(section = wust, flatTypes = Set(GroupId))
object Config {
  val usergroup: UsergroupConfig
  val auth: AuthConfig
  val email: Option[EmailConfig]
}
