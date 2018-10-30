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

package com.google.api.kotlin

import com.google.api.kgax.Retry
import com.google.api.kgax.RetryContext
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Duration
import com.google.rpc.Code
import com.google.rpc.Status
import com.google.showcase.v1alpha2.EchoClient
import com.google.showcase.v1alpha2.EchoRequest
import com.google.showcase.v1alpha2.ExpandRequest
import com.google.showcase.v1alpha2.PaginationRequest
import com.google.showcase.v1alpha2.WaitRequest
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout
import org.junit.AfterClass
import java.util.Random
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.fail

/**
 * Integration tests via Showcase:
 * https://github.com/googleapis/gapic-showcase
 */
class ShowcaseTest {

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
    fun `echos a request`() {
        val result = client.echo(
            EchoRequest { content = "Hi there!" }
        ).get()

        assertThat(result.body.content).isEqualTo("Hi there!")
    }

    // TODO: wrap the error
    @Test(expected = ExecutionException::class)
    fun `throws an error`() {
        try {
            client.echo(
                EchoRequest {
                    content = "junk"
                    error = Status {
                        code = Code.DATA_LOSS_VALUE
                        message = "oh no!"
                    }
                }
            ).get()
        } catch (e: Exception) {
            val error = e.cause as StatusRuntimeException
            assertThat(error.message).isEqualTo("DATA_LOSS: oh no!")
            assertThat(error.status.code.value()).isEqualTo(Code.DATA_LOSS_VALUE)
            throw e
        }
    }

    @Test
    fun `can expand a stream of responses`() {
        val c = Channel<Throwable?>()
        val expansions = mutableListOf<String>()

        val err = runBlockingWithTimeout {
            val stream = client.expand(ExpandRequest {
                content = "well hello there how are you"
            })

            stream.start {
                onNext = { expansions.add(it.content) }
                onError = { launch { c.send(it) } }
                onCompleted = { launch { c.send(null) } }
            }

            c.receive()
        }

        assertThat(err).isNull()
        assertThat(expansions).containsExactly("well", "hello", "there", "how", "are", "you").inOrder()
    }

    @Test
    fun `can expand a stream of responses and then error`() {
        val c = Channel<Throwable?>()
        val expansions = mutableListOf<String>()

        val err = runBlockingWithTimeout {
            val stream = client.expand(ExpandRequest {
                content = "one two zee"
                error = Status {
                    code = Code.ABORTED_VALUE
                    message = "yikes"
                }
            })

            stream.start {
                onNext = { expansions.add(it.content) }
                onError = { launch { c.send(it) } }
                onCompleted = { launch { c.send(null) } }
            }

            c.receive()
        }

        assertThat(err).isNotNull()
        assertThat(expansions).containsExactly("one", "two", "zee").inOrder()
    }

    @Test
    fun `can collect a stream of requests`() {
        val result = runBlockingWithTimeout {
            val stream = client.collect()

            stream.start()
            listOf("a", "b", "c", "done").map {
                stream.requests.send(EchoRequest { content = it })
            }
            stream.requests.close()

            stream.response.get()
        }

        assertThat(result.content).isEqualTo("a b c done")
    }

    @Test
    fun `can have a random chat`() {
        val c = Channel<Throwable?>()

        val inputs = Array(5) { _ ->
            Random().ints(20)
                .asSequence()
                .map { it.toString() }
                .joinToString("->")
        }

        val responses = mutableListOf<String>()
        val stream = client.chat()

        val err = runBlockingWithTimeout {
            stream.start {
                onNext = { responses.add(it.content) }
                onError = { launch { c.send(it) } }
                onCompleted = { launch { c.send(null) } }
            }

            for (str in inputs) {
                stream.requests.send(EchoRequest { content = str })
            }
            stream.requests.close()

            c.receive()
        }

        assertThat(err).isNull()
        assertThat(responses).containsExactly(*inputs).inOrder()
    }

    @Test
    fun `pages chucks of responses`() {
        val numbers = mutableListOf<Int>()
        var pageCount = 0

        val pager = client.pagination(PaginationRequest {
            pageSize = 10
            pageToken = "0"
            maxResponse = 39
        })

        for (page in pager) {
            numbers.addAll(page.elements)
            pageCount++
        }

        assertThat(pageCount).isEqualTo(4)
        assertThat(numbers).containsExactlyElementsIn((0 until 39).map { it })
        assertThat(pager.hasNext()).isFalse()
    }

    @Test
    fun `pages chucks of responses without pre-fetching`() {
        val pager = client.pagination(PaginationRequest {
            pageSize = 20
            pageToken = "0"
            maxResponse = 100
        })

        assertThat(pager.hasNext()).isTrue()

        val page = pager.next()

        assertThat(page.elements).containsExactlyElementsIn((0 until 20).map { it })
        assertThat(page.token).isNotEmpty()
        assertThat(pager.hasNext()).isTrue()
    }

    @Test
    fun `can wait and retry`() {
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
            }.wait(WaitRequest {
                responseDelay = Duration { seconds = 1 }
                error = Status {
                    code = Code.UNAVAILABLE_VALUE
                    message = "go away"
                }
            }).get()

            fail("expected to throw")
        } catch (t: Throwable) {
            assertThat(t.cause).isInstanceOf(StatusRuntimeException::class.java)
        }

        assertThat(retry.count).isEqualTo(6)
    }

    // standard 5 second timeout handler for streaming tests
    private fun <T> runBlockingWithTimeout(seconds: Long = 5, block: suspend CoroutineScope.() -> T) = runBlocking {
        withTimeout(seconds, TimeUnit.SECONDS, block)
    }
}
