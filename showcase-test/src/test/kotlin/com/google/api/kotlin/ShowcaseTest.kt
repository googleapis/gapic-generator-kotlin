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

import com.google.common.truth.Truth.assertThat
import com.google.rpc.Code
import com.google.rpc.Status
import com.google.showcase.v1.EchoClient
import com.google.showcase.v1.EchoRequest
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.OkHttpChannelBuilder
import org.junit.AfterClass
import java.util.concurrent.ExecutionException
import kotlin.test.Test

class ShowcaseTest {

    companion object {
        private val host = System.getenv("HOST") ?: "localhost"
        private val port = System.getenv("PORT") ?: "7469"

        val client = EchoClient.fromCredentials(
                channel = OkHttpChannelBuilder.forAddress(host, port.toInt())
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
}
