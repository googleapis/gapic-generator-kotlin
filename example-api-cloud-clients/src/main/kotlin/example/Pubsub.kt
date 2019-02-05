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
import com.google.pubsub.v1.ReceivedMessage
import com.google.pubsub.v1.SubscriberClient
import com.google.pubsub.v1.Topic
import com.google.pubsub.v1.pubsubMessage
import com.google.pubsub.v1.pushConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    launch { subscriber.start() }

    // wait for all the events to be published
    publisher.await()

    // give the subscriber some time to work
    delay(5_000)

    // shutdown
    publisher.stop()
    subscriber.stop()
}

/** Sends messages to a topic at a fixed rate on a background thread */
private class Publisher(
    projectId: String,
    topicName: String,
    val messagesToSend: Int = 20,
    val delayBetweenMessages: Long = 250
) {
    private val client by lazy {
        PublisherClient.fromEnvironment()
    }

    private val topic = "projects/$projectId/topics/$topicName"
    private lateinit var job: Job

    /** Start sending periodic messages` */
    suspend fun start(): Topic = coroutineScope {
        // create the topic to send on
        val topic = client.createTopic(topic).body
        println("Created topic: ${topic.name}")

        // send messages
        job = GlobalScope.launch(Dispatchers.IO) {
            repeat(messagesToSend) {
                println("Sending a message...")

                // send a new message
                client.publish(topic.name, listOf(
                    pubsubMessage {
                        data = "This is random: ${Math.random()}".asByteString()
                    }
                ))

                delay(delayBetweenMessages)
            }
            println("done sending!")
        }

        topic
    }

    /** Stop sending new messages */
    suspend fun await() {
        job.join()
    }

    suspend fun stop() {
        await()

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
    val delayBetweenPulls: Long = 100
) {
    private val client by lazy {
        SubscriberClient.fromEnvironment()
    }

    private val subscription = "projects/$projectId/subscriptions/$subscriptionName"
    private lateinit var job: Job

    /** Start listening and print incoming messages */
    suspend fun start() = coroutineScope {
        val sub = client.createSubscription(subscription, topic.name, pushConfig {}, 10).body
        println("Created subscription: ${sub.name}")

        // pool for new messages
        job = launch(Dispatchers.IO) {
            while (isActive) {
                // get new messages
                println("pulling new messages...")
                val result = client.pull(sub.name, true, 5).body
                if (result.receivedMessagesCount > 0) {
                    for (received in result.receivedMessagesList) {
                        println("Received message: ${received.asString()}")
                    }

                    // ack all of the messages
                    client.acknowledge(subscription, result.receivedMessagesList.map { it.ackId })
                } else {
                    println("no new messages!")
                }

                delay(delayBetweenPulls)
            }
        }
    }

    /** Stop listening for new messages */
    suspend fun stop() {
        job.cancelAndJoin()

        // clean up resources
        println("Removing subscription...")
        client.deleteSubscription(subscription)

        client.shutdownChannel()
    }
}

private fun String.asByteString() = ByteString.copyFrom(this, "UTF-8")
private fun ReceivedMessage.asString() = this.message.data.toString("UTF-8")
