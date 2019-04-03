/*
 * Copyright 2019 Google LLC
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

package com.google.api.kotlin

import com.google.common.truth.Truth.assertThat
import com.google.showcase.v1alpha3.MessagingClient
import com.google.showcase.v1alpha3.connectRequest
import com.google.showcase.v1alpha3.connectRequest_ConnectConfig
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test

/**
 * Integration tests via Showcase:
 * https://github.com/googleapis/gapic-showcase
 */
@ExperimentalCoroutinesApi
class MessagingTest {

    // client / connection to server
    companion object {
        private val host = System.getenv("HOST") ?: "localhost"
        private val port = System.getenv("PORT") ?: "7469"

        // use insecure client
        val client = MessagingClient.create(
            channel = ManagedChannelBuilder.forAddress(host, port.toInt())
                .usePlaintext()
                .build()
        )

        @AfterClass
        @JvmStatic
        fun destroyClient() {
            client.shutdownChannel()
        }
    }

    @Test
    fun `can have a quick chat`() = runBlocking {
        // create a new room
        val room = client.createRoom("room-${System.currentTimeMillis()}", "for chatty folks")

        // connect to the room
        val streams = client.connect()
        streams.requests.send(connectRequest {
            config = connectRequest_ConnectConfig { parent = room.name }
        })

        // aggregate all messages in the room
        val responses = mutableListOf<String>()
        val watchJob = launch {
            for (response in streams.responses) {
                val blurb = response.blurb
                responses.add("${blurb.user}: ${blurb.text}")
            }
        }

        client.createBlurb(room.name, "me", "hi there!")
        client.createBlurb(room.name, "you", "well, hello!")
        client.createBlurb(room.name, "me", "alright, that's enough!")
        client.createBlurb(room.name, "you", "bye!")

        // stop
        streams.requests.close()
        watchJob.join()
        client.deleteRoom(room.name)

        // verify
        assertThat(responses).containsExactly(
            "me: hi there!",
            "you: well, hello!",
            "me: alright, that's enough!",
            "you: bye!"
        ).inOrder()
    }
}
