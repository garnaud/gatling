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

import javax.jms.Message

import akka.actor.ActorRef
import io.gatling.core.action.{ Failable, Interruptable }
import io.gatling.core.session.{ Session, _ }
import io.gatling.core.util.TimeHelper._
import io.gatling.core.validation.{ SuccessWrapper, Validation }
import io.gatling.jms.client.SimpleJmsClient

/**
 * Core JMS Action to handle Forward and Forget semantics (no reply expected).
 * <p>
 * This handles the core "send"ing of messages. Gatling calls the execute method to trigger a send.
 * This implementation then forwards it on to a tracking actor.
 */
abstract class JmsAction(
  val next: ActorRef,
  attributes: JmsAttributes,
  protocol: JmsProtocol,
  tracker: ActorRef)
    extends Interruptable
    with Failable {

  // Create a client to refer to
  val client = new SimpleJmsClient(
    protocol.connectionFactoryName,
    attributes.destination,
    attributes.replyDestination,
    protocol.url,
    protocol.credentials,
    protocol.anonymousConnect,
    protocol.contextFactory,
    protocol.deliveryMode,
    protocol.messageMatcher)

  override def postStop(): Unit = {
    client.close()
  }

  def forwardToTracker(session: Session, start: Long, msg: Message): Unit

  /**
   * Framework calls the execute() method to send a single request
   * <p>
   * Note this does not catch any exceptions (even JMSException) as generally these indicate a
   * configuration failure that is unlikely to be addressed by retrying with another message
   */
  def executeOrFail(session: Session): Validation[Unit] = {

    // send the message
    val start = nowMillis

    val msg = resolveProperties(attributes.messageProperties, session).flatMap { messageProperties =>
      attributes.message match {
        case BytesJmsMessage(bytes) => bytes(session).map(bytes => client.sendBytesMessage(bytes, messageProperties))
        case MapJmsMessage(map)     => map(session).map(map => client.sendMapMessage(map, messageProperties))
        case ObjectJmsMessage(o)    => o(session).map(o => client.sendObjectMessage(o, messageProperties))
        case TextJmsMessage(txt)    => txt(session).map(txt => client.sendTextMessage(txt, messageProperties))
      }
    }

    msg.map(msg => forwardToTracker(session, start, msg))
  }

  def resolveProperties(properties: Map[Expression[String], Expression[Any]],
                        session: Session): Validation[Map[String, Any]] = {
    properties.foldLeft(Map.empty[String, Any].success) {
      case (resolvedProperties, (key, value)) =>
        val newProperty: Validation[(String, Any)] =
          for {
            key <- key(session)
            value <- value(session)
          } yield key -> value

        for {
          newProperty <- newProperty
          resolvedProperties <- resolvedProperties
        } yield resolvedProperties + newProperty
    }
  }

  def logMessage(text: String, msg: Message): Unit = {
    logger.debug(text)
    logger.trace(msg.toString)
  }
}

