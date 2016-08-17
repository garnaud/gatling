/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.jms

import java.util.concurrent.atomic.AtomicBoolean
import javax.jms.Message

import akka.actor.ActorRef
import io.gatling.core.Predef.Session
import io.gatling.core.util.TimeHelper.nowMillis

import scala.util.Try
import scala.util.control.NonFatal

object JmsReqReplyAction {
  val blockingReceiveReturnedNull = new Exception("Blocking receive returned null. Possibly the consumer was closed.")
}

/**
 * Core JMS Action to handle Request-Reply semantics
 * <p>
 * This handles the core "send"ing of messages. Gatling calls the execute method to trigger a send.
 * This implementation then forwards it on to a tracking actor.
 *
 * @author jasonk@bluedevel.com
 */
class JmsReqReplyAction(
  next: ActorRef,
  attributes: JmsAttributes,
  protocol: JmsProtocol,
  tracker: ActorRef)
    extends JmsAction(next, attributes, protocol, tracker) {

  val receiveTimeout = protocol.receiveTimeout.getOrElse(0L)
  val messageMatcher = protocol.messageMatcher

  class ListenerThread(val continue: AtomicBoolean = new AtomicBoolean(true)) extends Thread(new Runnable {
    def run(): Unit = {
      val replyConsumer = client.createReplyConsumer(attributes.selector.orNull)
      try {
        while (continue.get) {
          val m = replyConsumer.receive(receiveTimeout)
          m match {
            case msg: Message =>
              tracker ! MessageReceived(messageMatcher.responseID(msg), nowMillis, msg)
              logMessage(s"Message received ${msg.getJMSMessageID}", msg)
            case _ =>
              logger.error(JmsReqReplyAction.blockingReceiveReturnedNull.getMessage)
              throw JmsReqReplyAction.blockingReceiveReturnedNull
          }
        }
      } catch {
        // when we close, receive can throw exception
        case e: Exception => logger.error(e.getMessage)
      } finally {
        replyConsumer.close()
      }
    }
  }) {
    def close() = {
      continue.set(false)
      interrupt()
      join(1000)
    }
  }

  val listenerThreads = (1 to protocol.listenerCount).map(_ => new ListenerThread)

  listenerThreads.foreach(_.start)

  override def postStop(): Unit = {
    listenerThreads.foreach(thread => Try(thread.close()).recover { case NonFatal(e) => logger.warn("Could not shutdown listener thread", e) })
    client.close()
  }

  override def forwardToTracker(session: Session, start: Long, msg: Message): Unit = {
    // notify the tracker that a message was sent
    tracker ! MessageSent(msg.getJMSMessageID, start, nowMillis, attributes.checks, session, next, attributes.requestName)
    logMessage(s"Message sent ${msg.getJMSMessageID}", msg)
  }
}
