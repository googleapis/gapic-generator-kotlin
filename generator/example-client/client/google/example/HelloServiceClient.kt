// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package google.example

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.kgax.grpc.ClientCallOptions
import com.google.kgax.grpc.ClientCallOptions.Builder
import com.google.kgax.grpc.FutureCall
import com.google.kgax.grpc.GrpcClient
import com.google.kgax.grpc.GrpcClientStub
import com.google.kgax.grpc.prepare
import com.google.kgax.pager
import com.google.longrunning.OperationsGrpc
import com.google.longrunning.OperationsGrpc.OperationsFutureStub
import google.example.HelloServiceGrpc.HelloServiceFutureStub
import google.example.HelloServiceGrpc.HelloServiceStub
import io.grpc.ManagedChannel
import io.grpc.auth.MoreCallCredentials
import io.grpc.okhttp.OkHttpChannelBuilder
import java.io.InputStream
import javax.annotation.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Example API
 *
 * No configuration was provided for this API!
 *
 * [Product Documentation](http://www.google.com)
 */
@Generated("com.google.api.kotlin.generator.GRPCGenerator")
class HelloServiceClient private constructor(
    channel: ManagedChannel,
    options: ClientCallOptions,
    factory: Stubs.Factory? = null
) : GrpcClient(channel, options) {
    private val stubs: Stubs = factory?.create(channel, options) ?: Stubs(
        HelloServiceGrpc.newStub(channel).prepare(options),
        HelloServiceGrpc.newFutureStub(channel).prepare(options),
        OperationsGrpc.newFutureStub(channel).prepare(options)
    )

    /**
     * Prepare for an API call by setting any desired options. For example:
     *
     * ```
     * val client = HelloServiceClient.fromServiceAccount(YOUR_KEY_FILE)
     * val response = client.prepare {
     *     withMetadata("my-custom-header", listOf("some", "thing"))
     * }.hiThere(request).get()
     * ```
     *
     * You may save the client returned by this call and reuse it if you
     * plan to make multiple requests with the same settings.
     */
    fun prepare(init: ClientCallOptions.Builder.() -> Unit): HelloServiceClient {
        val options = ClientCallOptions.Builder(options)
        options.init()
        return HelloServiceClient(channel, options.build())
    }

    /**
     *
     *
     * For example:
     * ```
     * val client = HelloServiceClient.fromServiceAccount(YOUR_KEY_FILE)
     * val result = client.hiThere(
     *     HiRequest {
     *     }
     * )
     * ```
     *
     * @param request the request object for the API call
     */
    fun hiThere(request: HiRequest): FutureCall<HiResponse> = stubs.future.executeFuture {
        it.hiThere(request)
    }

    /**
     * Utilities for creating a fully configured HelloServiceClient.
     */
    companion object {
        @JvmStatic
        val ALL_SCOPES: List<String> = listOf("https://example.com/auth/scope")

        /**
         * Create a HelloServiceClient with the provided [accessToken].
         *
         * If a [channel] is not provided one will be created automatically (recommended).
         */
        @JvmStatic
        @JvmOverloads
        fun fromAccessToken(
            accessToken: AccessToken,
            scopes: List<String> = ALL_SCOPES,
            channel: ManagedChannel? = null
        ): HelloServiceClient {
            val credentials = GoogleCredentials.create(accessToken).createScoped(scopes)
            return HelloServiceClient(
                channel ?: createChannel(),
                ClientCallOptions(MoreCallCredentials.from(credentials))
            )
        }

        /**
         * Create a HelloServiceClient with service account credentials from a JSON [keyFile].
         *
         * If a [channel] is not provided one will be created automatically (recommended).
         */
        @JvmStatic
        @JvmOverloads
        fun fromServiceAccount(
            keyFile: InputStream,
            scopes: List<String> = ALL_SCOPES,
            channel: ManagedChannel? = null
        ): HelloServiceClient {
            val credentials = GoogleCredentials.fromStream(keyFile).createScoped(scopes)
            return HelloServiceClient(
                channel ?: createChannel(),
                ClientCallOptions(MoreCallCredentials.from(credentials))
            )
        }

        /**
         * Create a HelloServiceClient with the provided [credentials].
         *
         * If a [channel] is not provided one will be created automatically (recommended).
         */
        @JvmStatic
        @JvmOverloads
        fun fromCredentials(
            credentials: GoogleCredentials,
            channel: ManagedChannel? = null
        ): HelloServiceClient = HelloServiceClient(
            channel ?: createChannel(),
            ClientCallOptions(MoreCallCredentials.from(credentials))
        )

        /**
         * Create a HelloServiceClient with the provided gRPC stubs.
         *
         * This is an advanced method and should only be used when you need complete
         * control over the underlying gRPC stubs that are used by this client.
         *
         * Prefer to use [fromAccessToken], [fromServiceAccount], or [fromCredentials].
         */
        @JvmStatic
        @JvmOverloads
        fun fromStubs(
            factory: Stubs.Factory,
            channel: ManagedChannel? = null,
            options: ClientCallOptions? = null
        ): HelloServiceClient = HelloServiceClient(
            channel ?: createChannel(),
            options ?: ClientCallOptions(),
            factory
        )

        /**
         * Create a [ManagedChannel] to use with a HelloServiceClient.
         *
         * Prefer to use the default value with [fromAccessToken], [fromServiceAccount],
         * or [fromCredentials] unless you need to customize the channel.
         */
        @JvmStatic
        @JvmOverloads
        fun createChannel(
            host: String = "service.example.com",
            port: Int = 443,
            enableRetry: Boolean = true
        ): ManagedChannel {
            val builder = OkHttpChannelBuilder.forAddress(host, port)
            if (enableRetry) {
                builder.enableRetry()
            }
            return builder.build()
        }
    }

    class Stubs(
        val stream: GrpcClientStub<HelloServiceStub>,
        val future: GrpcClientStub<HelloServiceFutureStub>,
        val operation: GrpcClientStub<OperationsFutureStub>
    ) {
        interface Factory {
            fun create(channel: ManagedChannel, options: ClientCallOptions): Stubs
        }
    }
}
