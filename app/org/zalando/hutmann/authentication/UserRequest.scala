package org.zalando.hutmann.authentication

import org.zalando.hutmann.logging.RequestContext
import play.api.mvc.{ Request, WrappedRequest }

class UserRequest[A](val user: Either[AuthorizationProblem, User], request: Request[A])(implicit val context: RequestContext) extends WrappedRequest[A](request)
