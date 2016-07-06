package org.zalando.hutmann.authentication

import java.util.{ Base64, UUID }

import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import org.scalacheck.{ Arbitrary, Gen, Shrink }
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatestplus.play.OneAppPerSuite
import org.zalando.hutmann.logging.Context
import org.zalando.hutmann.spec.UnitSpec
import play.api.libs.ws.{ WS, WSClient }
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class OAuth2ActionTest extends UnitSpec with GeneratorDrivenPropertyChecks with OneAppPerSuite {
  implicit lazy val materializer: Materializer = app.materializer
  implicit lazy val ws: WSClient = WS.client

  implicit val testUser = Arbitrary {
    for {
      accessToken <- Gen.uuid.map(_.toString)
      username <- Gen.alphaStr
      expiryTime <- Arbitrary.arbInt.arbitrary
    } yield {
      User(accessToken, Map("uid" -> Some(username), "myscope" -> None), "/services", "Bearer", expiryTime, Some(username))
    }
  }

  def oauth2 = new OAuth2Action()(ConfigFactory.parseString(
    "org.zalando.hutmann.authentication.oauth2: {\ntokenInfoUrl: \"https://info.services.auth.zalando.com/oauth2/tokeninfo\"\ntokenQueryParam: \"access_token\"}"
  ), implicitly[ExecutionContext], implicitly[WSClient])

  def oauth2withMockedService(user: Either[AuthorizationProblem, User] = Right(testUser.arbitrary.sample.get), filter: User => Future[Boolean] = { user => Future.successful(true) }) =
    new OAuth2Action(filter)(ConfigFactory.parseString(
      "org.zalando.hutmann.authentication.oauth2: {\ntokenInfoUrl: \"https://info.services.auth.zalando.com/oauth2/tokeninfo\"\ntokenQueryParam: \"access_token\"}"
    ), implicitly[ExecutionContext], implicitly[WSClient]) {
      override def validateToken(token: String)(implicit context: Context): Future[Either[OAuth2Error, User]] = {
        Future.successful(user)
      }
    }

  //token can either ba some base64-coded string, or a uuid. should both work no matter what.
  lazy val tokenGen = Gen.oneOf(
    Gen.uuid.map(_.toString),
    Gen.alphaStr.map(string => Base64.getUrlEncoder.encodeToString(string.getBytes()).replace("=", "."))
  ) suchThat (_.nonEmpty)

  "OAuth2.getToken" should """work with tokens in the "Authorization" header""" in {
    forAll(tokenGen) { token: String =>
      oauth2.getToken(FakeRequest().withHeaders("Authorization" -> s"Bearer $token")) shouldBe Some(token)
    }
  }
  it should "work with tokens in the query parameter" in {
    forAll(tokenGen) { token: String =>
      oauth2.getToken(FakeRequest("GET", s"/?access_token=$token")) shouldBe Some(token)
    }
  }
  it should "work with tokens in both the query parameter and header if the tokens are equal" in {
    forAll(tokenGen) { token: String =>
      oauth2.getToken(FakeRequest("GET", s"/?access_token=$token").withHeaders("Authorization" -> s"Bearer $token")) shouldBe Some(token)
    }
  }
  it should """reject requests that have different tokens in the query parameter and the "Authorization" header""" in {
    forAll(tokenGen) { token: String =>
      oauth2.getToken(FakeRequest("GET", s"/?access_token=$token")
        .withHeaders("Authorization" -> s"Bearer ${UUID.randomUUID().toString}")) shouldBe None
    }
  }
  it should """reject requests that have the "access_token" header set more than once""" in {
    forAll(tokenGen) { token: String =>
      oauth2.getToken(FakeRequest("GET", s"/?access_token=$token&access_token=$token")) shouldBe None
    }
  }

  "OAuth2Action" should "accept a user with no filters" in {
    forAll { testUser: User =>
      val result = oauth2withMockedService(Right(testUser)).transform(FakeRequest("GET", s"/?access_token=${testUser.accessToken}")).futureValue
      result.user shouldBe Right(testUser)
    }
  }

  it should "accept a user where the filter applies correctly" in {
    forAll { testUser: User =>
      val result = oauth2withMockedService(Right(testUser), filter = Filters.scope("myscope")).transform(FakeRequest("GET", s"/?access_token=${testUser.accessToken}")).futureValue
      result.user shouldBe Right(testUser)
    }
  }
  it should "reject a user if the filter does not match" in {
    forAll { testUser: User =>
      val result = oauth2withMockedService(Right(testUser), filter = Filters.scope("mywrongscope")).transform(FakeRequest("GET", s"/?access_token=${testUser.accessToken}")).futureValue
      result.user shouldBe Left(InsufficientPermissions(testUser))
    }
  }
  it should "reject a user if the external system could not be reached" in {
    forAll(tokenGen) { token: String =>
      val result = oauth2withMockedService(Left(AuthorizationTimeout)).transform(FakeRequest("GET", s"/?access_token=$token")).futureValue
      result.user shouldBe Left(NoAuthorization)
    }
  }

  "OAuth2.transform" should "retry cases where we get a gateway timeout from the oauth service" in new OAuth2Action()(ConfigFactory.parseString(
    "org.zalando.hutmann.authentication.oauth2: {\ntokenInfoUrl: \"https://info.services.auth.zalando.com/oauth2/tokeninfo\"\ntokenQueryParam: \"access_token\"}"
  ), implicitly[ExecutionContext], implicitly[WSClient]) {
    @volatile var callCount = 0
    override def validateToken(token: String)(implicit context: Context): Future[Either[OAuth2Error, User]] = {
      val retVal = callCount match {
        case 0 => Future.successful(Left(TimeoutAuthError))
        case 1 => Future.successful(Left(AuthError("verybad_error", "something bad happened and we do not know why")))
      }
      callCount += 1
      retVal
    }

    forAll(tokenGen) { token: String =>
      callCount = 0
      transform(FakeRequest("GET", s"/?access_token=$token")).futureValue.user
      withClue("tested if validateToken was called the correct number of times"){ callCount shouldBe 2 }
    }(generatorDrivenConfig.copy(minSuccessful = 5), Shrink.shrinkAny)
  }
}
