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

import com.google.api.kgax.Retry
import com.google.api.kgax.RetryContext
import com.google.api.kgax.grpc.BasicInterceptor
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.duration
import com.google.rpc.Code
import com.google.rpc.status
import com.google.showcase.v1alpha3.EchoClient
import com.google.showcase.v1alpha3.PagedExpandResponse
import com.google.showcase.v1alpha3.echoRequest
import com.google.showcase.v1alpha3.expandRequest
import com.google.showcase.v1alpha3.pagedExpandRequest
import com.google.showcase.v1alpha3.waitRequest
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import java.util.Random
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.fail

/**
 * Integration tests via Showcase:
 * https://github.com/googleapis/gapic-showcase
 */
@ExperimentalCoroutinesApi
class EchoTest {

    // client / connection to server
    companion object {
        private val host = System.getenv("HOST") ?: "localhost"
        private val port = System.getenv("PORT") ?: "7469"

        // use insecure client
        val client = EchoClient.create(
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
    fun `echos a request`() = runBlocking<Unit> {
        val result = client.echo(
            echoRequest { content = "Hi there!" }
        )
echoRequest { `if` = "" }
        assertThat(result.body.content).isEqualTo("Hi there!")
    }

    @Test(expected = StatusRuntimeException::class)
    fun `throws an error`() = runBlocking<Unit> {
        try {
            client.echo(
                echoRequest {
                    content = "junk"
                    error = status {
                        code = Code.DATA_LOSS_VALUE
                        message = "oh no!"
                    }
                }
            )
        } catch (error: StatusRuntimeException) {
            assertThat(error.message).isEqualTo("DATA_LOSS: oh no!")
            assertThat(error.status.code.value()).isEqualTo(Code.DATA_LOSS_VALUE)
            throw error
        }
    }

    @Test
    fun `can expand a stream of responses`() = runBlocking<Unit> {
        val expansions = mutableListOf<String>()

        val streams = client.expand(expandRequest {
            content = "well hello there how are you"
        })

        for (response in streams.responses) {
            expansions.add(response.content)
        }

        assertThat(expansions).containsExactly("well", "hello", "there", "how", "are", "you").inOrder()
    }

    @Test
    fun `can expand a stream of responses and then error`() = runBlocking<Unit> {
        val expansions = mutableListOf<String>()

        val streams = client.expand(expandRequest {
            content = "one two zee"
            error = status {
                code = Code.ABORTED_VALUE
                message = "yikes"
            }
        })

        var error: Throwable? = null
        try {
            for (response in streams.responses) {
                expansions.add(response.content)
            }
        } catch (t: Throwable) {
            error = t
        }

        assertThat(error).isNotNull()
        assertThat(expansions).containsExactly("one", "two", "zee").inOrder()
    }

    @Test
    fun `can collect a stream of requests`() = runBlocking<Unit> {
        val streams = client.collect()

        listOf("a", "b", "c", "done").map {
            streams.requests.send(echoRequest { content = it })
        }
        streams.requests.close()

        assertThat(streams.response.await().content).isEqualTo("a b c done")
    }

    @Test
    fun `can have a random chat`() = runBlocking<Unit> {
        val inputs = Array(5) { _ ->
            Random().ints(20)
                .asSequence()
                .map { it.toString() }
                .joinToString("->")
        }

        val streams = client.chat()

        launch {
            for (str in inputs) {
                streams.requests.send(echoRequest { content = str })
            }
            streams.requests.close()
        }

        val responses = mutableListOf<String>()
        for (response in streams.responses) {
            responses.add(response.content)
        }

        assertThat(responses).containsExactly(*inputs).inOrder()
    }

    @Test
    fun `pages chucks of responses`() = runBlocking<Unit> {
        val numbers = mutableListOf<String>()
        var pageCount = 0

        val pager = client.pagedExpand(pagedExpandRequest {
            content = (0 until 40).joinToString(" ") { "num-$it" }
            pageSize = 10
            pageToken = "0"
        })

        for (page in pager) {
            numbers.addAll(page.elements.map { it.content })
            pageCount++
        }

        assertThat(pageCount).isEqualTo(4)
        assertThat(numbers).containsExactlyElementsIn((0 until 40).map { "num-$it" }).inOrder()
        assertThat(pager.isClosedForReceive).isTrue()
    }

    @Test
    fun `pages chucks of responses without pre-fetching`() = runBlocking<Unit> {
        var count = 0
        val pager = client
            .prepare {
                withInterceptor(BasicInterceptor(onMessage = {
                    count += (it as PagedExpandResponse).responsesCount
                }))
            }
            .pagedExpand(pagedExpandRequest {
                content = (0 until 59).joinToString(" ") { "x-$it" }
                pageSize = 5
                pageToken = "0"
            })
        assertThat(count).isIn(0..20)

        val page = pager.receive()

        assertThat(page.elements.map { it.content }).containsExactlyElementsIn((0 until 5).map { "x-$it" })
        assertThat(page.token).isNotEmpty()
        assertThat(count).isEqualTo(5)
        assertThat(pager.receive()).isNotNull()
        assertThat(count).isEqualTo(10)
        assertThat(pager.isClosedForReceive).isFalse()
    }

    @Test
    fun `can wait and retry`() = runBlocking {
        val retry = object : Retry {
            var count = 0
            override fun retryAfter(error: Throwable, context: RetryContext): Long? {
                count++
                return if (context.numberOfAttempts < 5) 50 else null
            }
        }

        try {
            client.prepare {
                withRetry(retry)
            }.wait(waitRequest {
                ttl = duration { seconds = 1 }
                error = status {
                    code = Code.UNAVAILABLE_VALUE
                    message = "go away"
                }
            }).await()

            fail("expected to throw")
        } catch (error: Throwable) {
            assertThat(error.message?.trim()?.replace("\\s+".toRegex(), " ")).isEqualTo(
                """
                |Operation completed with error: 14 details: go away
                """.trimMargin()
            )
        }

        // TODO: https://github.com/googleapis/gapic-showcase/issues/66
        assertThat(retry.count).isEqualTo(0)
    }
}
