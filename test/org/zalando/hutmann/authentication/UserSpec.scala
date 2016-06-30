package org.zalando.hutmann.authentication

import java.util.UUID

import User.app2AppUserReader
import org.zalando.hutmann.spec.UnitSpec
import play.api.libs.json.Json

import scala.util.Random

class UserSpec extends UnitSpec {
  val accessToken = UUID.randomUUID().toString
  val expiresIn = Random.nextInt(3600)

  val tokenWithoutScopes = Json.parse(
    s"""{"access_token": "$accessToken", "grant_type": "password", """ +
      s""""scope": [ ], "realm": "employees", "token_type": "Bearer", "expires_in": $expiresIn }"""
  )
  val tokenWithUidCnScopes = Json.parse(
    s"""{"access_token": "$accessToken", "uid": "unicorn", "grant_type": "password", """ +
      s""""scope": [ "uid", "cn" ], "realm": "employees", "cn": "", "token_type": "Bearer", "expires_in": $expiresIn }"""
  )
  val tokenWithVariousScopes = Json.parse(
    s"""{"access_token": "$accessToken", "uid": "fluffy_unicorn", "grant_type": "password", """ +
      """"scope": [ "uid", "cn", "blafoo.check" ], "realm": "employees", "cn": "", "token_type": "Bearer",""" +
      s""" "blafoo.check":"test", "expires_in": $expiresIn }"""
  )
  val tokenWithVariousScopesAndMissingScopeInformation = Json.parse(
    s"""{"access_token": "$accessToken", "uid": "barbaz", "grant_type": "password",""" +
      """ "scope": [ "uid", "cn", "blafoo.check" ], "realm": "employees", "cn": "", "token_type": "Bearer",""" +
      s""" "blafoo.check2":"test", "expires_in": $expiresIn }"""
  )

  "A valid token without scopes" should "be parsed correctly" in {
    tokenWithoutScopes.as[User] should equal(
      User(accessToken, Map(), realm = "employees", tokenType = "Bearer", expiresIn, uid = None)
    )
  }
  "A valid token with only uid and cn scopes" should "be parsed correctly" in {
    tokenWithUidCnScopes.as[User] should equal(
      User(
        accessToken,
        Map("uid" -> Some("unicorn"), "cn" -> Some("")),
        realm = "employees",
        tokenType = "Bearer",
        expiresIn,
        uid = Some("unicorn")
      )
    )
  }
  "A valid token with uid, cn and various other scopes" should "be parsed correctly" in {
    tokenWithVariousScopes.as[User] should equal(
      User(
        accessToken,
        Map("uid" -> Some("fluffy_unicorn"), "cn" -> Some(""), "blafoo.check" -> Some("test")),
        realm = "employees",
        tokenType = "Bearer",
        expiresIn,
        uid = Some("fluffy_unicorn")
      )
    )
  }
  "A token with missing scope information" should "be be parsed indicating that the scope information is missing" in {
    tokenWithVariousScopesAndMissingScopeInformation.as[User] should equal(
      User(
        accessToken,
        Map("uid" -> Some("barbaz"), "cn" -> Some(""), "blafoo.check" -> None),
        realm = "employees",
        tokenType = "Bearer",
        expiresIn,
        uid = Some("barbaz")
      )
    )
  }
}
