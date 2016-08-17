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

import akka.actor.ActorDSL.actor
import akka.actor.ActorRef
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.Protocols

case class JmsNoReplyActionBuilder(attributes: JmsAttributes) extends ActionBuilder {

  /**
   * Builds an action instance
   */
  def build(next: ActorRef, registry: Protocols) = {
    val jmsProtocol = registry.getProtocol[JmsProtocol].getOrElse(throw new UnsupportedOperationException("JMS protocol wasn't registered"))
    val tracker = actor(actorName("jmsRequestTracker"))(new JmsRequestTrackerActor)
    actor(actorName("jmsNoReply"))(new JmsNoReplyAction(next, attributes, jmsProtocol, tracker))
  }
}
