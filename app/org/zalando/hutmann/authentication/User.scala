package org.zalando.hutmann.authentication

import play.api.libs.functional.syntax._
import play.api.libs.json._

final case class User(
  accessToken: String,
  scope:       Map[String, Option[String]],
  realm:       String,
  tokenType:   String,
  expiresIn:   Int,
  uid:         Option[String]
) extends OAuth2User

object User {
  implicit val app2AppUserReader: Reads[User] = new Reads[User] {
    override def reads(json: JsValue): JsResult[User] = {
      json.validate[IntermediateUser](IntermediateApp2AppUser.basicReads) match {
        case JsSuccess(intermediate, jsPath) =>
          val scopeMap: Map[String, Option[String]] =
            (for { scopeElement <- intermediate.scope } yield {
              (scopeElement, (json \ scopeElement).validate[String].asOpt)
            }).toMap

          JsSuccess(
            User(
              accessToken = intermediate.accessToken,
              scope = scopeMap,
              realm = intermediate.realm,
              tokenType = intermediate.tokenType,
              expiresIn = intermediate.expiresIn,
              uid = (json \ "uid").asOpt[String]
            ), jsPath
          )
        case error: JsError => error
      }
    }
  }

}

/**
  * Intermediate user that will only be used internally. Do not use this one directly, use App2AppUser instead.
  */
private final case class IntermediateUser(
  accessToken: String,
  scope:       Set[String],
  realm:       String,
  tokenType:   String,
  expiresIn:   Int
)
private object IntermediateApp2AppUser {
  val basicReads = (
    (JsPath \ "access_token").read[String] and
    (JsPath \ "scope").read[Set[String]] and
    (JsPath \ "realm").read[String] and
    (JsPath \ "token_type").read[String] and
    (JsPath \ "expires_in").read[Int]
  )(IntermediateUser.apply _)
}
