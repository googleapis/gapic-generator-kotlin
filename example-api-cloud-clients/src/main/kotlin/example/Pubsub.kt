/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package example

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PublisherClient
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.PushConfig
import com.google.pubsub.v1.ReceivedMessage
import com.google.pubsub.v1.SubscriberClient
import com.google.pubsub.v1.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Date

/**
 * Simple example of calling the PubSub API with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ GOOGLE_APPLICATION_CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --args pubsub
 * ```
 */
fun pubSubExample() = runBlocking {
    // get the project id
    val projectId = System.getenv("PROJECT")
        ?: throw RuntimeException("You must set the PROJECT environment variable to run this example")
    val topicName = "topic-${Date().time}"
    val subscriptionName = "sub-${Date().time}"

    // start sending messages
    val publisher = Publisher(projectId, topicName)
    val topic = publisher.start()

    // start receiving messages
    val subscriber = Subscriber(projectId, subscriptionName, topic)
    subscriber.start()

    // wait for all the events to be published
    publisher.await()

    // shutdown the subscriber
    subscriber.stop()
}

/** Sends messages to a topic at a fixed rate on a background thread */
private class Publisher(
    projectId: String,
    topicName: String,
    val messagesToSend: Int = 20,
    val delayBetweenMessages: Long = 500
) {
    private val client by lazy {
        PublisherClient.fromEnvironment()
    }

    private val topic = "projects/$projectId/topics/$topicName"
    private lateinit var job: Job

    /** Start sending periodic messgages */
    suspend fun start(): Topic = coroutineScope {
        // create the topic to send on
        val topic = client.createTopic(topic).body

        // send messages
        job = launch(Dispatchers.IO) {
            repeat(messagesToSend) {
                println("Sending a message...")

                // send a new message
                client.publish(topic.name, listOf(
                    PubsubMessage {
                        data = "it's now: ${Date().time}".asByteString()
                    }
                ))

                delay(delayBetweenMessages)
            }
        }

        topic
    }

    /** Stop sending new messages */
    suspend fun await() {
        job.join()

        // clean up resources
        println("Removing topic...")
        client.deleteTopic(topic)

        client.shutdownChannel()
    }
}

/** Reads messages from a topic on a background thread */
private class Subscriber(
    projectId: String,
    subscriptionName: String,
    val topic: Topic,
    val delayBetweenPulls: Long = 500
) {
    private val client by lazy {
        SubscriberClient.fromEnvironment()
    }

    private val subscription = "projects/$projectId/subscriptions/$subscriptionName"
    private lateinit var job: Job
    private var running = false

    /** Start listening and print incoming messages */
    suspend fun start() = coroutineScope {
        val sub = client.createSubscription(subscription, topic.name, PushConfig {}, 10).body

        // pool for new messages
        running = true
        job = launch(Dispatchers.IO) {
            while (running) {
                // get a new message
                val result = client.pull(sub.name, false, 5).body
                for (received in result.receivedMessagesList) {
                    println("Received message: ${received.asString()}")
                }

                // ack all of the messages
                client.acknowledge(subscription, result.receivedMessagesList.map { it.ackId })

                delay(delayBetweenPulls)
            }
        }
    }

    /** Stop listening for new messages */
    suspend fun stop() {
        running = false
        job.join()

        // clean up resources
        println("Removing subscription...")
        client.deleteSubscription(subscription)

        client.shutdownChannel()
    }
}

private fun String.asByteString() = ByteString.copyFrom(this, "UTF-8")
private fun ReceivedMessage.asString() = this.message.data.toString("UTF-8")
