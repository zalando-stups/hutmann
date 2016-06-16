package org.zalando.hutmann.authentication

import play.api.libs.functional.syntax._
import play.api.libs.json.{ JsPath, Reads }

final case class AuthError(error: String, errorDescription: String) extends OAuth2Error
final case object TimeoutAuthError extends OAuth2Error

object AuthError {
  implicit val format: Reads[AuthError] = (
    (JsPath \ "error").read[String] and
    (JsPath \ "error_description").read[String]
  )(AuthError.apply _)
}
