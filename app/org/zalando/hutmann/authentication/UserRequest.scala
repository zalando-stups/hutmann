package org.zalando.hutmann.authentication

import play.api.mvc.{ Request, WrappedRequest }

import scala.concurrent.Future

class UserRequest[A](val user: Either[AuthorizationProblem, User], request: Request[A]) extends WrappedRequest[A](request)
