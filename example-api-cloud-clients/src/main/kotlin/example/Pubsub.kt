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
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Simple example of calling the PubSub API with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ GOOGLE_APPLICATION_CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --args pubsub
 * ```
 */
fun pubSubExample() {
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

    // wait a little...
    Thread.sleep(10_000)

    // stop
    publisher.stop()
    subscriber.stop()
}

/** Sends messages to a topic at a fixed rate on a background thread */
private class Publisher(
    projectId: String,
    topicName: String,
    val messagesPerSecond: Long = 1
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()

    private val client by lazy {
        PublisherClient.fromEnvironment()
    }

    private val topic = "projects/$projectId/topics/$topicName"

    /** Start sending periodic messgages */
    fun start(): Topic {
        // create the topic to send on
        val topic = client.createTopic(topic).get().body

        // send messages
        executor.scheduleAtFixedRate({
            println("Sending a message...")

            // send a new message
            client.publish(topic.name, listOf(
                PubsubMessage {
                    data = "it's now: ${Date().time}".asByteString()
                }
            ))
        }, 0, messagesPerSecond, TimeUnit.SECONDS)
        return topic
    }

    /** Stop sending new messages */
    fun stop() {
        executor.shutdownNow()

        // clean up resources
        println("Removing topic...")
        client.deleteTopic(topic).get()

        client.shutdownChannel()
    }
}

/** Reads messages from a topic on a background thread */
private class Subscriber(
    projectId: String,
    subscriptionName: String,
    val topic: Topic
) {
    private val executor = Executors.newSingleThreadExecutor()

    private val client by lazy {
        SubscriberClient.fromEnvironment()
    }

    private val subscription = "projects/$projectId/subscriptions/$subscriptionName"

    /** Start listening and print incoming messages */
    fun start() {
        val sub = client.createSubscription(subscription, topic.name, PushConfig {}, 10).get().body

        executor.submit {
            while (true) {
                // get a new message
                val result = client.pull(sub.name, false, 5).get().body
                for (received in result.receivedMessagesList) {
                    println("Received message: ${received.asString()}")
                }

                // ack all of the messages
                client.acknowledge(subscription, result.receivedMessagesList.map { it.ackId })
            }
        }
    }

    /** Stop listening for new messages */
    fun stop() {
        executor.shutdownNow()

        // clean up resources
        println("Removing subscription...")
        client.deleteSubscription(subscription).get()

        client.shutdownChannel()
    }
}

private fun String.asByteString() = ByteString.copyFrom(this, "UTF-8")
private fun ReceivedMessage.asString() = this.message.data.toString("UTF-8")
