package org.zalando.hutmann.authentication

import play.api.Logging
import play.api.libs.json.{ JsError, JsSuccess, JsValue }

trait OAuth2Response

final case class OAuth2Success[T <: OAuth2User](user: T) extends OAuth2Response
final case class OAuth2Failure[T <: OAuth2Error](failure: T) extends OAuth2Response

object OAuth2Response extends Logging {
  implicit val userFormat = User.app2AppUserReader
  implicit val errorFormat = AuthError.format

  def fromJson(json: JsValue): OAuth2Response = {
    json.validate[User] match {
      case JsSuccess(user, _) => OAuth2Success(user)
      case JsError(error) =>
        json.validate[AuthError] match {
          case JsSuccess(failure, _) => OAuth2Failure(failure)
          case JsError(fail) =>
            logger.warn("Failed to parse oauth response from auth server")
            OAuth2Failure(AuthError("Parser failed", "Failed to parse response from OAuth server."))
        }
    }
  }
}
