package wust.backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.igl.jwt._

import wust.api._

object Claims {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val userFormat = (
    (__ \ "id").format[Long] ~
    (__ \ "name").format[String] ~
    (__ \ "isImplicit").format[Boolean] ~
    (__ \ "revision").format[Int]
  )(User.apply, unlift(User.unapply))

  case class UserClaim(value: User) extends ClaimValue {
    override val field: ClaimField = UserClaim
    override val jsValue: JsValue = Json.toJson(value)
  }
  object UserClaim extends ClaimField {
    override def attemptApply(value: JsValue): Option[ClaimValue] =
      value.asOpt[User].map(apply)

    override val name = "user"
  }
}

case class JWTAuthentication(user: User, expires: Long, token: Authentication.Token) {
  def toAuthentication = Authentication(user, token)
}

object JWT {
  import Claims.UserClaim

  type Token = String

  private val secret = "Gordon Shumway" //TODO
  private val algorithm = Algorithm.HS256
  private val wustIss = Iss("wust")
  private val wustAud = Aud("wust")
  private def currentTimestamp: Long = System.currentTimeMillis / 1000

  private def expirationTimestamp = currentTimestamp + 86400 // 24h

  def generateToken(user: User, expires: Long): DecodedJwt = new DecodedJwt(
    Seq(Alg(algorithm), Typ("JWT")),
    Seq(wustIss, wustAud, Exp(expires), UserClaim(user))
  )

  def generateAuthentication(user: User): JWTAuthentication = {
    val expires = expirationTimestamp
    val jwt = generateToken(user, expires)
    JWTAuthentication(user, expires, jwt.encodedAndSigned(secret))
  }

  def authenticationFromToken(token: Authentication.Token): Option[JWTAuthentication] = {
    DecodedJwt.validateEncodedJwt(
      token, secret, algorithm, Set(Typ),
      Set(Iss, Aud, Exp, UserClaim),
      iss = Option(wustIss), aud = Option(wustAud)
    ).toOption.flatMap { decoded =>
      for {
        expires <- decoded.getClaim[Exp]
        user <- decoded.getClaim[UserClaim]
      } yield {
        JWTAuthentication(user.value, expires.value, token)
      }
    }.filterNot(isExpired)
  }

  def isExpired(auth: JWTAuthentication): Boolean = auth.expires <= currentTimestamp
}
