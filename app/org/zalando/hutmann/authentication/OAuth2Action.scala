package org.zalando.hutmann.authentication

import java.util.concurrent.TimeoutException

import com.typesafe.config.Config
import org.zalando.hutmann.logging.{ Context, Logger, RequestContext }
import play.api.Play.{ configuration, current }
import play.api.http.Status
import play.api.libs.ws.{ WS, WSRequest }
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{ Failure, Success, Try }

/**
  * A play action that authorizes a user using the Zalando OAuth2 server
  *
  * @param filter A function `User => Boolean` to determine if a authorized user is also authorized for this action
  * @param config The config section containing the OAuth server information
  */
class OAuth2Action(
  val filter:         User => Future[Boolean] = { user: User => Future.successful(true) },
  val autoReject:     Boolean                 = true,
  val requestTimeout: Duration                = 1.seconds
)(implicit config: Config, ec: ExecutionContext)
    extends ActionBuilder[UserRequest] {

  val url = config.getString("org.zalando.hutmann.authentication.oauth2.tokenInfoUrl")
  val query = config.getString("org.zalando.hutmann.authentication.oauth2.tokenQueryParam")

  val queryParamTokenPattern = Patterns.queryParamTokenPattern
  val headerTokenPattern = Patterns.headerTokenPattern

  val logger = Logger( /*"org.zalando.hutmann.oauth2"*/ )

  def validateToken(token: String)(implicit context: Context): Future[Either[OAuth2Error, User]] = {
    val request: WSRequest = WS.url(url)
      .withQueryString(query -> token)
      .withHeaders("Accept" -> "application/json")
      .withRequestTimeout(requestTimeout.toMillis)
    // Todo Only allow communication via HTTPS, check certificates (e.g. only specific root CA allowed)
    request.get().map(resp => {
      Try(OAuth2Response.fromJson(resp.json)) match {
        case Success(OAuth2Success(user: User))       => Right(user)
        case Success(OAuth2Failure(error: AuthError)) => Left(error)
        case Failure(NonFatal(ex)) => resp.status match {
          case Status.GATEWAY_TIMEOUT => Left(TimeoutAuthError)
          case _ =>
            logger.error(s"${ex.toString} with response code ${resp.status}, body '${resp.body}'")
            Left(AuthError("invalid_reponse", s"response code ${resp.status}, body '${resp.body}'"))
        }
      }
    })
  }

  /**
    * Reads a token from the request `Authorization` header
    */
  private def getTokenFromHeader(rh: RequestHeader): Option[String] = {
    for {
      headerString <- rh.headers.get("Authorization")
      token <- headerTokenPattern.findFirstMatchIn(headerString).map(_.group("token"))
    } yield token
  }
  /**
    * Reads a token from the `access_token` query parameter
    */
  def getTokenFromQueryParameter(rh: RequestHeader): Option[String] = {
    val tokens = for {
      queryStrings <- rh.queryString.get(query).toSeq
      queryString <- queryStrings
      token <- queryParamTokenPattern.findFirstMatchIn(queryString).map(_.group("token"))
    } yield token

    //only exactly one token is good, all other cases are bad.
    tokens match {
      case Nil            => None
      case Seq(token)     => Some(token)
      case Seq(token, _*) => None
    }
  }

  /**
    * Reads a token from either the query parameter, or the header. Checks that - given the case we get a token in both
    * places - the tokens are consistent.
    */
  def getToken(rh: RequestHeader): Option[String] = {
    val headerTokenOpt = getTokenFromHeader(rh)
    val queryParamTokenOpt = getTokenFromQueryParameter(rh)

    (headerTokenOpt, queryParamTokenOpt) match {
      case (Some(headerToken), Some(queryToken)) =>
        if (headerToken == queryToken) {
          headerTokenOpt
        } else {
          None
        }
      case _ => headerTokenOpt.orElse(queryParamTokenOpt)
    }
  }

  /**
    * Scores the types of problems that can occur and maps them to appropriate user-usable ones.
    */
  def tokenResultScoring[A](implicit context: Context): (Either[OAuth2Error, User]) => Future[Either[AuthorizationProblem, User]] = {
    case Right(user) =>
      filter(user).map { isValidUser =>
        if (isValidUser) {
          Right(user)
        } else {
          logger.info("User is authorized but doesn't fit the given filter")
          Left(InsufficientPermissions(user))
        }
      }
    case Left(failure) =>
      logger.info(s"Failed to validate token: $failure")
      Future.successful(Left(NoAuthorization))
  }

  /**
    * Add the authorization information to the request.
    */
  def transform[A](request: Request[A]): Future[UserRequest[A]] = {
    implicit val context: Context = request
    val tokenOpt: Option[String] = getToken(request)
    tokenOpt match {
      case Some(token) =>
        val futureToken = validateToken(token).recoverWith {
          case NonFatal(ex) =>
            logger.warn("Problem getting OAuth token, retrying...", ex)
            validateToken(token)
        }.flatMap{
          case Left(TimeoutAuthError) =>
            logger.warn("Gateway timeout while getting OAuth token, retrying...")
            validateToken(token)
          case other => Future.successful(other)
        }
        val futureUser = futureToken.flatMap(tokenResultScoring).recoverWith({
          case _: TimeoutException => Future.successful(Left(AuthorizationTimeout))
          case e                   => throw e
        })
        futureUser.map(new UserRequest(_, request))
      case None =>
        logger.info("No authorization founder in 'Authorization' header or 'access_token' query parameter. Rejecting access.")
        Future.successful(new UserRequest(Left(NoAuthorization), request))
    }
  }

  override def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[Result]): Future[Result] = {
    transform(request).flatMap(userRequest =>
      if (autoReject) {
        autoRejectBehaviour(userRequest.user, block(userRequest))
      } else {
        block(userRequest)
      }) recoverWith recoveryBehaviour(request)
  }

  /**
    * Function that describes the behaviour of the OAuth2 action builder in case of an auto reject. This can be used in an
    * application as well to map the default behaviour at a later time (e.g. after the time-costly operations, when the
    * result of the OAuth2 token service is available.
    *
    * @param   userResult The result of the user token checking
    * @param   result The result of your operation (maybe unfinished)
    * @return Either your result, or "forbidden"/"unauthorized"/"gateway timeout".
    */
  def autoRejectBehaviour(userResult: Either[AuthorizationProblem, User], result: => Future[Result]): Future[Result] =
    userResult match {
      case Right(user)                      => result
      case Left(InsufficientPermissions(_)) => Future.successful(Forbidden)
      case Left(NoAuthorization)            => Future.successful(Unauthorized)
      case Left(AuthorizationTimeout)       => Future.successful(GatewayTimeout)
    }

  /**
    * Function that describes how to behave when an exception is caught. This function may as well just re-throw the exception,
    * which forces Play to handle that problem. Default-Behaviour is to answer with 500 - internal server error, and log that exception
    * together with the flow id.
    */
  def recoveryBehaviour[A](request: RequestHeader): PartialFunction[scala.Throwable, Future[Result]] = PartialFunction{
    case NonFatal(ex) =>
      implicit val context: RequestContext = request
      logger.error("internal error while executing service", ex)
      Future.successful(Results.InternalServerError)
  }
}

object OAuth2Action {
  def withUserFilter(
    filter: User => Boolean
  )(implicit ec: ExecutionContext): OAuth2Action =
    apply({ user: User => Future.successful(filter(user)) })

  def apply(
    filter: User => Future[Boolean] = { user: User => Future.successful(true) }
  )(implicit ec: ExecutionContext): OAuth2Action =
    apply(filter, autoReject = true, 1.second)

  def apply(
    filter:         User => Future[Boolean],
    autoReject:     Boolean,
    requestTimeout: Duration
  )(implicit ec: ExecutionContext): OAuth2Action =
    new OAuth2Action(filter, autoReject, requestTimeout)(configuration.underlying, ec)
}

/** Holds the regular expression patterns that are used for reading the tokens from headers and query parameters. Extracted here due to performance reasons.*/
object Patterns {
  val queryParamTokenPattern = new Regex("^(?i)(.+)$", "token")
  val headerTokenPattern = new Regex("^(?i)(Bearer (.+))$", "all", "token")
}
