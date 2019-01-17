/*
 * Copyright (C) 2017 VSCT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.vsct.tock.bot.connector.teams

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.salomonbrys.kodein.instance
import com.microsoft.bot.schema.models.Activity
import fr.vsct.tock.bot.connector.ConnectorBase
import fr.vsct.tock.bot.connector.ConnectorCallback
import fr.vsct.tock.bot.connector.ConnectorData
import fr.vsct.tock.bot.engine.BotRepository
import fr.vsct.tock.bot.engine.ConnectorController
import fr.vsct.tock.bot.engine.action.SendSentence
import fr.vsct.tock.bot.engine.event.Event
import fr.vsct.tock.bot.engine.monitoring.logError
import fr.vsct.tock.bot.engine.user.PlayerId
import fr.vsct.tock.bot.engine.user.PlayerType
import fr.vsct.tock.shared.Executor
import fr.vsct.tock.shared.error
import fr.vsct.tock.shared.injector
import fr.vsct.tock.shared.jackson.mapper
import mu.KotlinLogging
import java.time.Duration

/**
 *
 */
class TeamsConnector(
    val applicationId: String,
    private val path: String
) : ConnectorBase(teamsConnectorType) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val client = TeamsClient()
    private val executor: Executor by injector.instance()

    override fun register(controller: ConnectorController) {
        controller.registerServices(path) { router ->

            router.post(path).handler {context ->
                val requestTimerData = BotRepository.requestTimer.start("teams handler")
                try {
                    //TODO: check authenticity of messenger via applicationId + password
                    val body = context.bodyAsString
                    println(body)
                    val activity: Activity = mapper.readValue(body)
                    executor.executeBlocking {
                        val e: Event? = SendSentence(
                                PlayerId("toto"),
                                applicationId,
                                PlayerId(applicationId, PlayerType.bot),
                                activity.text()
                        )
                        if (e != null) {
                            controller.handle(
                                e,
                                ConnectorData(
                                        TeamsConnectorCallback(applicationId, activity)
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.logError(e, requestTimerData)
                } finally {
                    try {
                        BotRepository.requestTimer.end(requestTimerData)
                        context.response().end()
                    } catch (e: Throwable) {
                        logger.error(e)
                    }
                }
            }
        }
    }

    override fun send(event: Event, callback: ConnectorCallback, delayInMs: Long) {
        if (event is SendSentence && callback is TeamsConnectorCallback) {

            val delay = Duration.ofMillis(delayInMs)
            executor.executeBlocking(delay) {
                client.sendMessage(callback.activity, event)
            }
        }
    }
}