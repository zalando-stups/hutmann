package org.zalando.hutmann.authentication

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Contains filters for the authentication that can be used to filter requests
  */
object Filters {
  /**
    * Checks if a user has all the given scopes (AND-combination of all scopes)
    *
    * @param scopes All mandatory scopes that are needed on a user to pass this check.
    */
  def allScopes(scopes: String*): User => Future[Boolean] = { user: User =>
    Future.successful(scopes.forall(user.scope.keySet.contains))
  }

  /**Checks if a user has the given scope, or not*/
  def scope(scope: String): User => Future[Boolean] = { user: User =>
    Future.successful(user.scope.keySet.contains(scope))
  }

  /**
    * Checks if a user has at least one of the given scopes.
    * Use case: {{{scope("myscope.all") || scope("myscope.write") == atLeastOneScope("myscope.all", "myscope.write"}}}
    */
  def atLeastOneScope(scopes: String*): User => Future[Boolean] = { user: User =>
    Future.successful(scopes.exists(user.scope.keySet.contains))
  }

  /**Checks whether the user has a uid, or not*/
  val hasUid: User => Future[Boolean] = { user: User =>
    Future.successful(user.uid.isDefined)
  }

  /**Checks whether the user comes from a given realm, or not*/
  def fromRealm(realm: String): User => Future[Boolean] = { user: User =>
    Future.successful(user.realm == realm)
  }

  /**Checks whether the given token is a bearer token*/
  val bearerToken: User => Future[Boolean] = { user: User =>
    Future.successful(user.tokenType == "Bearer")
  }

  /**
    * Implicit conversion that makes it easy to combine other filters in a readable fashion. Enables the following syntax:
    * {{{scopes("myscope.all") || kohleTeamMember}}}, which allows access to users with the given scopes, or members of team kohle although
    * they do not have the given scopes.
    *
    * {{{scopes("myscope.all")}}}
    */
  implicit class FilterCompositor(val filter: User => Future[Boolean]) extends AnyVal {
    //scalastyle:off method.name
    /**Combines two filters with a logical OR. Uses short-cuts if possible, therefore only calls the second service if it is actually needed*/
    def ||(otherFilter: User => Future[Boolean])(implicit ec: ExecutionContext): User => Future[Boolean] = { user: User =>
      filter(user).flatMap{
        case true  => Future.successful(true)
        case false => otherFilter(user)
      }
    }
    /**Combines two filters with a logical AND. Uses short-cuts if possible, therefore only calls the second service if it is actually needed*/
    def &&(otherFilter: User => Future[Boolean])(implicit ec: ExecutionContext): User => Future[Boolean] = { user: User =>
      filter(user).flatMap{
        case true  => otherFilter(user)
        case false => Future.successful(false)
      }
    }
    //scalastyle:on method.name
  }
}
