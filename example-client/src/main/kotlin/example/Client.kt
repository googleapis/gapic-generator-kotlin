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

import google.example.HelloServiceClient
import google.example.HiRequest
import io.grpc.ManagedChannelBuilder

class Client {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) = Client().runExample()
    }

    fun runExample() {
        // create a client with an insecure channel
        val client = HelloServiceClient.fromCredentials(channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                .usePlaintext()
                .build())

        // call the API
        val result = client.hiThere(HiRequest {
            query = "Hello!"
        }).get()

        // print the result
        println("The response was: ${result.body.result}")

        // shutdown
        client.shutdownChannel()
    }
}
