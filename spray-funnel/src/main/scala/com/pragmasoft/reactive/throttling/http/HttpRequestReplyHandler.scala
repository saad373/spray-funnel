package com.pragmasoft.reactive.throttling.http

import akka.actor.{ActorSystem, ActorRefFactory, Props, ActorRef}
import com.pragmasoft.reactive.throttling.threshold.Frequency
import scala.concurrent.duration.FiniteDuration
import com.pragmasoft.reactive.throttling.actors.{RequestReplyHandler, RequestReplyThrottlingCoordinator}
import spray.http.{HttpResponse, HttpRequest}
import com.pragmasoft.reactive.throttling.actors.handlerspool.{OneActorPerRequestPool, FixedSizePool, HandlerFactory}
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import akka.io
import spray.can.Http
import spray.util._
import com.pragmasoft.reactive.throttling.threshold.Frequency
import scala.concurrent.duration._
import com.pragmasoft.reactive.throttling.http.HttpRequestThrottling.HttpThrottlingConfiguration

object HttpRequestReplyHandler {
  def props(coordinator: ActorRef) = Props(classOf[HttpRequestReplyHandler], coordinator)
}

class HttpRequestReplyHandler(coordinator: ActorRef) extends RequestReplyHandler[HttpResponse](coordinator: ActorRef)


abstract class AbstractHttpRequestReplyThrottlingCoordinator(
                                                              transport: ActorRef,
                                                              frequencyThreshold: Frequency,
                                                              requestTimeout: FiniteDuration,
                                                              requestExpiry: Duration,
                                                              maxQueueSize: Int
                                                              ) extends RequestReplyThrottlingCoordinator[HttpRequest](transport, frequencyThreshold, requestTimeout, requestExpiry, maxQueueSize) with HandlerFactory {

  def createHandler() = context.actorOf(HttpRequestReplyHandler.props(self))
}

class FixedPoolSizeHttpRequestReplyThrottlingCoordinator(
                                                          transport: ActorRef,
                                                          frequencyThreshold: Frequency,
                                                          requestTimeout: FiniteDuration,
                                                          val poolSize: Int,
                                                          requestExpiry: Duration,
                                                          maxQueueSize: Int
                                                          ) extends AbstractHttpRequestReplyThrottlingCoordinator(transport, frequencyThreshold, requestTimeout, requestExpiry, maxQueueSize) with FixedSizePool


class HttpRequestReplyThrottlingCoordinator(
                                             transport: ActorRef,
                                             frequencyThreshold: Frequency,
                                             requestTimeout: FiniteDuration,
                                             requestExpiry: Duration,
                                             maxQueueSize: Int
                                             ) extends AbstractHttpRequestReplyThrottlingCoordinator(transport, frequencyThreshold, requestTimeout, requestExpiry, maxQueueSize) with OneActorPerRequestPool


object HttpRequestReplyCoordinator {
  def propsForFrequencyAndParallelRequestsWithTransport(frequencyThreshold: Frequency, maxParallelRequests: Int, transport: ActorRef, requestTimeout: Timeout) =
    Props(classOf[FixedPoolSizeHttpRequestReplyThrottlingCoordinator], transport, frequencyThreshold, requestTimeout.duration, maxParallelRequests)

  def propsForFrequencyWithTransport(frequencyThreshold: Frequency, transport: ActorRef, requestTimeout: Timeout) =
    Props(classOf[FixedPoolSizeHttpRequestReplyThrottlingCoordinator], transport, frequencyThreshold, requestTimeout.duration)

  def propsForFrequencyAndParallelRequests(frequencyThreshold: Frequency, maxParallelRequests: Int)
                                          (implicit refFactory: ActorRefFactory, executionContext: ExecutionContext,requestTimeout: Timeout = 60.seconds) =
    Props(classOf[FixedPoolSizeHttpRequestReplyThrottlingCoordinator], io.IO(Http)(actorSystem), frequencyThreshold, requestTimeout.duration, maxParallelRequests, Duration.Inf, 0)

  def propsForFrequency(frequencyThreshold: Frequency)
                       (implicit refFactory: ActorRefFactory, executionContext: ExecutionContext, requestTimeout: Timeout = 60.seconds) =
    Props(classOf[HttpRequestReplyThrottlingCoordinator], io.IO(Http)(actorSystem), frequencyThreshold, requestTimeout.duration, Duration.Inf, 0)

  def propsForConfig(config: HttpThrottlingConfiguration)(implicit actorSystem : ActorSystem, executionContext: ExecutionContext) : Props = {
    if( (config.requestConfig.parallelThreshold > 0) && (config.requestConfig.parallelThreshold != Int.MaxValue) ) {
      Props(
        classOf[FixedPoolSizeHttpRequestReplyThrottlingCoordinator],
        io.IO(Http)(actorSystem),
        config.frequencyThreshold,
        config.requestConfig.timeout.duration,
        config.requestConfig.parallelThreshold,
        config.requestConfig.expiry,
        config.requestConfig.maxQueueSize
      )
    } else {
      Props(
        classOf[HttpRequestReplyThrottlingCoordinator],
        io.IO(Http)(actorSystem),
        config.frequencyThreshold,
        config.requestConfig.timeout.duration,
        config.requestConfig.expiry,
        config.requestConfig.maxQueueSize
      )
    }
  }

}