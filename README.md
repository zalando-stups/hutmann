[![Build Status](https://travis-ci.org/zalando-incubator/hutmann.svg?branch=master)](https://travis-ci.org/zalando-incubator/hutmann)
[![Coverage Status](https://coveralls.io/repos/github/zalando-incubator/hutmann/badge.svg?branch=master)](https://coveralls.io/github/hutmann/hutmann?branch=master)
[![MIT licensed](https://img.shields.io/badge/license-MIT-green.svg)](https://raw.githubusercontent.com/zalando-incubator/hutmann/master/LICENSE)

# Hutmann - a Scala/Play library for OAuth2 authentication

This library provides support for using OAuth2 for authentication, mainly for backends, that use the Play framework (2.4). Additionally, it brings
some support for logging and flow ids that can be used to follow requests through several microservices. It should
not require much setup before using it - see `Configuration` for more details.

The name stems from the german mining term [Hutmann](https://de.wikipedia.org/wiki/Hutmann), who was responsible to make sure that no
unauthorized person drove into the pit.

## Features

 * OAuth2 handling for backend services
 * Extended logging functionality
 * Flow ID handling

## Key concepts

We tried to minimize the needed user setup if you want to have a working minimal solution. There is a special play
`Action` that you can use to mark services that require authentication, so places that have beforehand been like
```scala
import org.zalando.hutmann.authentication.OAuth2Action
import play.api.mvc._
import play.api.mvc.Results._

  def heartbeat = Action {
    Ok("<3")
  }
```

become

```scala
import scala.concurrent.ExecutionContext
import play.api.libs.ws.WSClient
import play.api.Configuration

//these come from the application normally
implicit val ws: WSClient = null
implicit val config: Configuration = null
import scala.concurrent.ExecutionContext.Implicits.global

  def heartbeat = OAuth2Action()(implicitly[ExecutionContext], implicitly[WSClient], implicitly[Configuration]) { 
    Ok("<3")
  }
```

and it automatically makes sure that requests to that route have a valid authentication token - although most probably,
you won't make your heartbeat endpoint secured.

# Related projects / Why this one?

tell something about the other frameworks etc

# Getting started

## Perquisites and dependencies

The library currently only works with Play 2.4. It depends on its JSON and WS libraries, which your project must bring.

## Getting it

Versioning follows the Play version number it works with. 2.4.x therefore is a version that works with Play 2.4, 2.5.x (if any) works with Play 2.5.

```scala
libraryDependencies += "org.zalando" %% "hutmann" % "2.5.1"
```

## Configuring

If you are a user of the Zalando OAuth2 infrastructure, you don't need to configure anything. The following configuration
keys may be interesting though:

```json
org.zalando.hutmann.authentication.oauth2: {
  tokenInfoUrl: "https://info.services.auth.zalando.com/oauth2/tokeninfo"
  tokenQueryParam: "access_token"
}
```

# Feature explanation and their usage
## OAuth2Action
The `OAuth2Action` is used to secure your backend endpoints. It is made to return simple error messages, but you are free to extend the
implementation if you need it by simply subclassing the Action. Besides the simple usage example that you already found above, this is a
more elaborate example:

```scala
import org.zalando.hutmann.authentication.Filters._
def heartbeat = OAuth2Action(scope("myservice.read"))(implicitly[ExecutionContext], implicitly[WSClient], implicitly[Configuration]) {
  Ok("<3")
}
```

will only allow the users with scope `myservice.read`, and only if he has a valid access token, while

```scala
def heartbeat = OAuth2Action(isEmployee)(implicitly[ExecutionContext], implicitly[WSClient], implicitly[Configuration]) {
  Ok("<3")
}
```

or use(recommended) [EssentialAction](https://www.playframework.com/documentation/2.5.x/ScalaEssentialAction) to authenticate request first and then proceed with everything else like request body parsing etc.
side effects of not using EssentialAction is explained in detail in this [issue](https://github.com/zalando-incubator/hutmann/issues/8)

```scala
import scala.concurrent.ExecutionContext
import play.api.libs.ws.WSClient
import play.api.Configuration
import scala.util.Future

//these come from the application normally
implicit val ws: WSClient = null
implicit val config: Configuration = null
import scala.concurrent.ExecutionContext.Implicits.global

def heartbeat = OAuth2Action()(implicitly[ExecutionContext], implicitly[WSClient], implicitly[Configuration]).essentialAction(parse.default) { 
    Future.successful(Ok("<3"))
}
```


will check if the token is from realm "/employees" and has a scope "uid" property set.

In both cases, the body of the action will only get called when the user may access it, and error responses are generated accordingly. If you do not
want this behaviour, but care yourself for the handling, you have two possibilities:

* You can extend `OAuth2Action` in your project and `override def autoRejectBehaviour`, which allows you to specify in which case you want
you want what behaviour. This is the recommended approach.
* You can set `autoReject = false` as a parameter, and have a look at the request property
`user` which is either a user, or an `AuthorizationProblem`. This is basically "doing everything by hand".

The default timeout for contacting the server is 1 second, a request will be repeated once in case a timeout occurs, with circuit breakers applied.

#### Create own authorization / using your own servers
You can do so by subclassing OAuth2Action and `override def validateToken`.

#### How to mock values in tests?
Just create this in your project:

```scala
import scala.concurrent.duration._
import scala.concurrent.Future
import com.typesafe.config.Config

import org.zalando.hutmann.authentication._
trait Auth {
  def authAction(
      filter:     User => Future[Boolean] = { user: User => Future.successful(true) },
      autoReject: Boolean                 = true,
      requestTimeout: Duration            = 1.second
    )(implicit config: Config, ec: ExecutionContext, ws: WSClient): OAuth2Action =
      new OAuth2Action(filter, autoReject, requestTimeout)
}
trait FakeAuth extends Auth {
  val userResult: Either[AuthorizationProblem, User]
  override def authAction(
      filter:     User => Future[Boolean] = { user: User => Future.successful(true) },
      autoReject: Boolean                 = true,
      requestTimeout: Duration            = 1.second
    )(implicit config: Config, ec: ExecutionContext, ws: WSClient): OAuth2Action =
      new OAuth2Action(filter, autoReject, requestTimeout) {
        override def transform[A](request: Request[A]): Future[UserRequest[A]] =
          Future.successful(new UserRequest(userResult, request)(request))
      }
}
```
You now only need to mix in `Auth` in your controller, and `FakeAuth` in your controller in your tests, additionally provide the
result you'd like to have in the `FakeAuth`.

#### How to respond with custom responses for 401, 403 and 504 cases?
Extend `OAuth2Action` and override `autoRejectBehaviour`. You can copy the initial implementation and adjust for your needs.

## Filters
Play filters are used to run right after the routing and before invoking the action. This makes them particularly useful for cases like

* Logging request information (headers and response codes)
* Adding flow information (flow ids) to the requests

Refer to the [Play documentation](https://www.playframework.com/documentation/2.4.x/ScalaHttpFilters) on how to add filters to your project.

### LoggingFilter

This one is used to log headers (including flow ids) and response codes of your requests, as well as the duration between an incoming request
and the outgoing response.

### FlowIdFilter

**What is a Flow ID?** A flow id is a specific header that a service consumes and sends to all connected services that are called from the same flow.
This flow id shall additionally be added to all log entries of your services. This way, you can track your requests through your microservice infrastructure.

The `FlowIdFilter` inspects the requests that come in if they have a flow id and - depending on the configuration - either adds one if it there is none,
 or rejects the request.

 There are four FlowIdFilter implementations:

 `final class CreateFlowIdFilter`
 `final class StrictFlowIdFilter`

 `final class MdcCreateFlowIdFilter`
 `final class MdcStrictFlowIdFilter`

 The two last ones sets the **Flow Id** value in the SLF4J MDC, so that it can be used in the logs just adding ```X{X-Flow-ID}``` to the log pattern in yout logback.xml file.


 #### How to use it

 In to your filters file:
 ```tut:silent
     |  import javax.inject.Inject
     |  import play.api.http.DefaultHttpFilters
     |  import org.zalando.hutmann.filters.[FlowIdFilterImplementation]
<console>:52: error: identifier expected but '[' found.
 import org.zalando.hutmann.filters.[FlowIdFilterImplementation]
                                    ^
     | 
     |  class Filters @Inject() (
     |    flowIdFilter: [FlowIdFilterImplementation],
<console>:54: error: identifier expected but '[' found.
   flowIdFilter: [FlowIdFilterImplementation],
                 ^
     |    /*...your other filters ... */
     |  ) extends DefaultHttpFilters(flowIdFilter, /*...*/)
<console>:55: error: illegal start of simple expression
 ) extends DefaultHttpFilters(flowIdFilter, /*...*/)
                                                   ^
     | 
 ```


 The Mdc FlowIdFilter implementations works capturing the request in a thread local, making it available to any code executed by the Play default execution context. In order to make it works you need to configure the akka dispatcher as following:

 ```
 akka {
   actor {
     default-dispatcher {
       type: org.zalando.hutmann.dispatchers.ContextPropagatingDispatcherConfigurator
     }
   }
 }
 ```


## More logging

You can use the integrated logger in your projects as well, and benefit from the automatic context evaluation that will take care that you
have your flow ids in every log entry.

```scala
import org.zalando.hutmann.logging._
import play.api.libs.json._

object MyController extends Controller {
  def doSomething(implicit context: Context): Unit = {
    Logger.warn("watch out!")
  }

  def createSession: Action[JsValue] = OAuth2Action()(implicitly[ExecutionContext], implicitly[WSClient], implicitly[Configuration])(parse.tolerantJson) { request =>
    implicit val context: Context = request //there is an implicit conversion for the request
    doSomething
    Logger.info("some message")
    Created
  }
}
```


```scala
MyController.createSession(FakeRequest().withJsonBody(Json.obj()))
```


```
09:52:00.578 [pool-1-thread-1-ScalaTest-running-LoggerDemo] WARN application - watch out! - 1ms/TheSourceFile.scala:15/THEFLOWID
09:52:00.578 [pool-1-thread-1-ScalaTest-running-LoggerDemo] INFO application - some message - 1ms/TheSourceFile.scala:20/THEFLOWID
```

will automatically log the passed time since the beginning of the flow (creation of the context), the flow id, and the source code line where the
log event was created.
