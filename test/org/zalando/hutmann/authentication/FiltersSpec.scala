package org.zalando.hutmann.authentication

import java.util.UUID

import org.zalando.hutmann.spec.UnitSpec

class FiltersSpec extends UnitSpec {
  def userWithScopes(scopes: String*) = User(UUID.randomUUID().toString, Map(scopes.map(_ -> None): _*), "test", "Bearer", 3600, Some("testuser"))

  "Filters.scope" should "succeed if that scope exists for the user" in {
    val filter = Filters.scope("myscope.all")
    filter(userWithScopes("myscope.all")).futureValue shouldBe true
  }
  it should "fail if the scope does not exist for the user" in {
    val filter = Filters.scope("myscope.write")
    filter(userWithScopes("myscope.read")).futureValue shouldBe false
  }

  "Filters.allScopes" should "succeed if the user has all the scopes" in {
    val filter = Filters.allScopes("myscope.write", "myscope.read")
    filter(userWithScopes("myscope.write", "myscope.read")).futureValue shouldBe true
    //ordering should not matter
    filter(userWithScopes("myscope.read", "myscope.write")).futureValue shouldBe true
  }
  it should "fail if the user misses one scope" in {
    val filter = Filters.allScopes("myscope.write", "myscope.read")
    filter(userWithScopes("myscope.write")).futureValue shouldBe false
    filter(userWithScopes("myscope.read")).futureValue shouldBe false
  }
  it should "fail if the user misses all scopes" in {
    val filter = Filters.allScopes("myscope.write", "myscope.read")
    filter(userWithScopes()).futureValue shouldBe false
  }

  "Filters.atLeastOneScope" should "succeed if all scopes are present" in {
    val filter = Filters.atLeastOneScope("myscope.write", "myscope.read")
    filter(userWithScopes("myscope.write", "myscope.read")).futureValue shouldBe true
    //ordering should not matter
    filter(userWithScopes("myscope.read", "myscope.write")).futureValue shouldBe true
  }
  it should "succeed if just one of the scopes is given" in {
    val filter = Filters.atLeastOneScope("myscope.write", "myscope.read")
    filter(userWithScopes("myscope.write")).futureValue shouldBe true
    //ordering should not matter
    filter(userWithScopes("myscope.read")).futureValue shouldBe true
  }
  it should "fail if none of the scopes is given" in {
    val filter = Filters.allScopes("myscope.write", "myscope.read")
    filter(userWithScopes()).futureValue shouldBe false
  }

  "Filters.hasUid" should "succeed for users with a uid scope" in {
    Filters.hasUid(userWithScopes()).futureValue shouldBe true
  }
  it should "fail for users without a uid scope" in {
    Filters.hasUid(userWithScopes().copy(uid = None)).futureValue shouldBe false
  }

  "Filters.fromRealm" should "succeed for users from the given realm" in {
    val filter = Filters.fromRealm("test")
    filter(userWithScopes()).futureValue shouldBe true
  }
  it should "fail for users from another realm" in {
    val filter = Filters.fromRealm("anotherrealm")
    filter(userWithScopes()).futureValue shouldBe false
  }

  "Filters.bearerToken" should "succeed for bearer tokens" in {
    Filters.bearerToken(userWithScopes()).futureValue shouldBe true
  }
  it should "fail for other tokens" in {
    Filters.bearerToken(userWithScopes().copy(tokenType = "Other")).futureValue shouldBe false
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  "Filters" should "be composable with &&" in {
    import Filters._
    val andFilter = scope("myscope.read") && scope("myscope.write")
    andFilter(userWithScopes()).futureValue shouldBe false
    andFilter(userWithScopes("myscope.read")).futureValue shouldBe false
    andFilter(userWithScopes("myscope.write")).futureValue shouldBe false
    andFilter(userWithScopes("myscope.read", "myscope.write")).futureValue shouldBe true

  }
  it should "be composable with ||" in {
    import Filters._
    val andFilter = scope("myscope.read") || scope("myscope.write")
    andFilter(userWithScopes()).futureValue shouldBe false
    andFilter(userWithScopes("myscope.read")).futureValue shouldBe true
    andFilter(userWithScopes("myscope.write")).futureValue shouldBe true
    andFilter(userWithScopes("myscope.read", "myscope.write")).futureValue shouldBe true
  }
}
