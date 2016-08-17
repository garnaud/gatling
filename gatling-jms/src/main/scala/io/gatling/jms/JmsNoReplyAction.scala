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
import io.gatling.core.Predef.Session
import io.gatling.core.util.TimeHelper.nowMillis

/**
 * Core JMS Action to handle Forward and Forget semantics (no reply expected).
 * <p>
 * This handles the core "send"ing of messages. Gatling calls the execute method to trigger a send.
 * This implementation then forwards it on to a tracking actor.
 */
class JmsNoReplyAction(
  next: ActorRef,
  attributes: JmsAttributes,
  protocol: JmsProtocol,
  tracker: ActorRef)
    extends JmsAction(next, attributes, protocol, tracker) {

  override def forwardToTracker(session: Session, start: Long, msg: Message): Unit = {
    // notify the tracker that a message was sent
    tracker ! MessageSentWithNoReply(msg.getJMSMessageID, start, nowMillis, session, next, attributes.requestName)
    logMessage(s"Message sent ${msg.getJMSMessageID}", msg)
  }

}