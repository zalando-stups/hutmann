package org.zalando.hutmann.helpers

import akka.actor.{ ActorSystem, Scheduler }
import akka.pattern.CircuitBreaker
import play.api.Configuration
import play.api.Play._
import play.api.libs.concurrent.Akka

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps

/**
  * Creates circuit breaker instances to be used in the application. Please see
  * conf/reference.conf for an example for how to configure it.
  */
object CircuitBreakerFactory {
  private val baseConfigKey = "org.zalando.hutmann.circuitBreaker"

  private def configAsDuration(breaker: String, key: String)(implicit configuration: Configuration): Option[FiniteDuration] = configuration
    .getMilliseconds(s"$baseConfigKey.$breaker.$key")
    .orElse(configuration.getMilliseconds(s"$baseConfigKey.generic.$key"))
    .map(_.millis)
  private def configAsInt(breaker: String, key: String)(implicit configuration: Configuration): Option[Int] = configuration
    .getInt(s"$baseConfigKey.$breaker.$key")
    .orElse(configuration.getInt(s"$baseConfigKey.generic.$key"))

  /**
    * @param breaker The name of the circuit-breaker in the config, if any (reads "generic" if the given one is undefined)
    * @return The maximum number of failures to trigger the circuit-breaker.
    */
  def maxFailures(breaker: String)(implicit configuration: Configuration): Int = {
    val defaultMaxFailures = 5
    configAsInt(breaker, "maxFailures").getOrElse(defaultMaxFailures)
  }

  /**
    * @param breaker The name of the circuit-breaker in the config, if any (reads "generic" if the given one is undefined)
    * @return The call timeout to trigger a failure in the circuit-breaker.
    */
  def callTimeout(breaker: String)(implicit configuration: Configuration): FiniteDuration = {
    configAsDuration(breaker, "callTimeout").getOrElse(10 seconds)
  }

  /**
    * @param breaker The name of the circuit-breaker in the config, if any (reads "generic" if the given one is undefined)
    * @return The reset timeout to trigger a failure in the circuit-breaker.
    */
  def resetTimeout(breaker: String)(implicit configuration: Configuration): FiniteDuration = {
    configAsDuration(breaker, "resetTimeout").getOrElse(30 seconds)
  }

  /**
    * Create a new circuit breaker with settings from the config.
    *
    * @param name The name of the scheduler
    * @return A circuit breaker. Share it between all resources needing to circuit-break, do *not* just create a new one
    *         with the same name. They won't be connected in any way.
    */
  def apply(name: Option[String] = None)(implicit configuration: Configuration, actorSystem: ActorSystem): CircuitBreaker = {
    val circuitBreakerName = name.getOrElse("generic")
    CircuitBreaker(
      actorSystem.scheduler,
      maxFailures(circuitBreakerName),
      callTimeout(circuitBreakerName),
      resetTimeout(circuitBreakerName)
    ) //TODO: implement onOpen, onClose, onHalfOpen with meters for metrics endpoint?
  }
}
