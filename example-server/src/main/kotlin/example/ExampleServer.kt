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

import google.example.HelloServiceGrpc
import google.example.HiRequest
import google.example.HiResponse
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import java.io.IOException

/**
 * Example server.
 *
 * Adapted from the gRPC examples:
 * https://github.com/grpc/grpc-java/blob/master/examples/example-kotlin
 */
class ExampleServer(private val port: Int = 8080) {

    companion object {

        /** Main launches the server from the command line. */
        @JvmStatic
        @Throws(IOException::class, InterruptedException::class)
        fun main(args: Array<String>) {
            val server = ExampleServer()
            server.start()
            server.blockUntilShutdown()
        }
    }

    private var server: Server? = null

    @Throws(IOException::class)
    private fun start() {
        server = ServerBuilder.forPort(port)
                .addService(HelloServiceImpl())
                .build()
                .start()

        println("Server started on port: $port")

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                println("Shutting down gRPC server...")
                this@ExampleServer.stop()
                println("Server shut down.")
            }
        })
    }

    private fun stop() = server?.shutdown()

    /** Await termination on the main thread since the grpc library uses daemon threads. */
    @Throws(InterruptedException::class)
    private fun blockUntilShutdown() = server?.awaitTermination()

    /** HelloService implementation */
    private class HelloServiceImpl : HelloServiceGrpc.HelloServiceImplBase() {

        override fun hiThere(request: HiRequest, responseObserver: StreamObserver<HiResponse>) {
            val reply = HiResponse.newBuilder().apply {
                result = "Hi there! You said: ${request.query}"
            }.build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
