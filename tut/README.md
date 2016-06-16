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
```tut:silent
import org.zalando.hutmann.authentication.OAuth2Action
import play.api.mvc._
import play.api.mvc.Results._

  def heartbeat = Action {
    Ok("<3")
  }
```

become

```tut:silent
  def heartbeat = OAuth2Action() {
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

```scala
libraryDependencies += "org.zalando" %% "hutmann" % "0.1"
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

```tut:silent
def heartbeat = OAuth2Action(_.uid.contains("jdoe")) {
  Ok("<3")
}
```

will only allow the user `jdoe`, and only if he has a valid access token, while

```tut:silent
def heartbeat = OAuth2Action(_.scope.isDefinedAt("my_service.read_all")) {
  Ok("<3")
}
```

will check if the service user has the scope `my_service.read_all`. Sometimes, a `scope` can hold additional information - like `uid`, which just is a special
scope - therefore it's a map, and you can have a look in there as well.

In both cases, the body of the action will only get called when the user may access it, and error responses are generated accordingly. If you do not
want this behaviour, but care yourself for the handling, you have two possibilities:

* You can extend `OAuth2Action` in your project and `override def autoRejectBehaviour`, which allows you to specify in which case you want
you want what behaviour. This is the recommended approach.
* You can set `autoReject = false` as a parameter, and have a look at the request property
`user` which is either a user, or an `AuthorizationProblem`. This is basically "doing everything by hand".

The default timeout for contacting the server is 1 second, a request will be repeated once in case a timeout occurs, with circuit breakers applied.

#### Create own authorization
You can do so by subclassing OAuth2Action and `override def validateToken`.

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

## More logging

You can use the integrated logger in your projects as well, and benefit from the automatic context evaluation that will take care that you
have your flow ids in every log entry.

```tut:silent
import org.zalando.hutmann.logging.Logger
import org.zalando.hutmann.logging.Context
import play.api.libs.json._

object MyController extends Controller {
  def doSomething(implicit context: Context): Unit = {
    Logger.warn("watch out!")
  }

  def createSession: Action[JsValue] = OAuth2Action()(parse.tolerantJson) { request =>
    implicit val context: Context = request //there is an implicit conversion for the request
    doSomething
    Logger.info("some message")
    Created
  }
}
```
```tut:invisible
import play.api.test._
import play.api._
val app = FakeApplication()
Play.start(app)
```
```tut:silent
MyController.createSession(FakeRequest().withJsonBody(Json.obj()))
```
```tut:invisible
Play.stop(app)
```
```
09:52:00.578 [pool-1-thread-1-ScalaTest-running-LoggerDemo] WARN application - watch out! - 1ms/TheSourceFile.scala:15/THEFLOWID
09:52:00.578 [pool-1-thread-1-ScalaTest-running-LoggerDemo] INFO application - some message - 1ms/TheSourceFile.scala:20/THEFLOWID
```

will automatically log the passed time since the beginning of the flow (creation of the context), the flow id, and the source code line where the
log event was created.
