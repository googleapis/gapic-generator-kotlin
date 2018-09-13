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

package com.google.api.kotlin.generator.grpc

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.ServiceOptions
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests basic functionality.
 *
 * more complex tests use the test protos in [GRPCGeneratorTest].
 */
internal class CompanionObjectImplTest {

    private val ctx: GeneratorContext = mock()
    private val meta: Configuration = mock()
    private val serviceOptions: ServiceOptions = mock()

    @BeforeTest
    fun before() {
        reset(ctx, meta, serviceOptions)
        whenever(ctx.metadata).doReturn(meta)
        whenever(ctx.serviceOptions).doReturn(serviceOptions)
    }

    @Test
    fun `Generates default scopes property`() {
        whenever(serviceOptions.scopes).doReturn(listOf("a", "b c d e"))
        whenever(ctx.className).doReturn(ClassName("foo.bar", "ScopesTest"))

        val type = CompanionObjectImpl().generate(ctx)

        assertThat(type.propertySpecs).hasSize(1)

        val prop = type.propertySpecs.first { it.name == "ALL_SCOPES" }
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |@kotlin.jvm.JvmStatic
            |val ALL_SCOPES: kotlin.collections.List<kotlin.String> = listOf("a", "b c d e")
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates factory with access token`() {
        whenever(serviceOptions.scopes).doReturn(listOf("a", "b c d e"))
        whenever(ctx.className).doReturn(ClassName("r.r.r", "Clazz"))

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "fromAccessToken" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Create a Clazz with the provided [accessToken].
            |*
            |* If a [channel] is not provided one will be created automatically (recommended).
            |*/
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun fromAccessToken(
            |    accessToken: com.google.auth.oauth2.AccessToken,
            |    scopes: kotlin.collections.List<kotlin.String> = ALL_SCOPES,
            |    channel: io.grpc.ManagedChannel? = null
            |): r.r.r.Clazz {
            |    val credentials = com.google.auth.oauth2.GoogleCredentials.create(accessToken).createScoped(scopes)
            |    return r.r.r.Clazz(
            |        channel ?: createChannel(),
            |        com.google.kgax.grpc.ClientCallOptions(io.grpc.auth.MoreCallCredentials.from(credentials))
            |    )
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates factory with credentials`() {
        whenever(serviceOptions.scopes).doReturn(listOf("a", "b c d e"))
        whenever(ctx.className).doReturn(ClassName("r.r.r", "Clazz"))

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "fromCredentials" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Create a Clazz with the provided [credentials].
            |*
            |* If a [channel] is not provided one will be created automatically (recommended).
            |*/
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun fromCredentials(
            |    credentials: com.google.auth.oauth2.GoogleCredentials? = null,
            |    channel: io.grpc.ManagedChannel? = null
            |): r.r.r.Clazz {
            |    val cred = credentials?.let { io.grpc.auth.MoreCallCredentials.from(it) }
            |    return r.r.r.Clazz(
            |        channel ?: createChannel(),
            |        com.google.kgax.grpc.ClientCallOptions(cred)
            |    )
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates factory with serviceAccount`() {
        whenever(serviceOptions.scopes).doReturn(listOf("a", "b c d e"))
        whenever(ctx.className).doReturn(ClassName("r.r.r", "Clazz"))

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "fromServiceAccount" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Create a Clazz with service account credentials from a JSON [keyFile].
            |*
            |* If a [channel] is not provided one will be created automatically (recommended).
            |*/
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun fromServiceAccount(
            |    keyFile: java.io.InputStream,
            |    scopes: kotlin.collections.List<kotlin.String> = ALL_SCOPES,
            |    channel: io.grpc.ManagedChannel? = null
            |): r.r.r.Clazz {
            |    val credentials = com.google.auth.oauth2.GoogleCredentials.fromStream(keyFile).createScoped(scopes)
            |    return r.r.r.Clazz(
            |        channel ?: createChannel(),
            |        com.google.kgax.grpc.ClientCallOptions(io.grpc.auth.MoreCallCredentials.from(credentials))
            |    )
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates factory with stubs`() {
        whenever(serviceOptions.scopes).doReturn(listOf("a", "b c d e"))
        whenever(ctx.className).doReturn(ClassName("r.r.r", "Clazz"))

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "fromStubs" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Create a Clazz with the provided gRPC stubs.
            |*
            |* This is an advanced method and should only be used when you need complete
            |* control over the underlying gRPC stubs that are used by this client.
            |*
            |* Prefer to use [fromAccessToken], [fromServiceAccount], or [fromCredentials].
            |*/
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun fromStubs(
            |    factory: Stubs.Factory,
            |    channel: io.grpc.ManagedChannel? = null,
            |    options: com.google.kgax.grpc.ClientCallOptions? = null
            |): r.r.r.Clazz = r.r.r.Clazz(
            |    channel ?: createChannel(),
            |    options ?: com.google.kgax.grpc.ClientCallOptions(), factory
            |)
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates create channel method`() {
        whenever(serviceOptions.scopes).doReturn(listOf("x", "y"))
        whenever(ctx.className).doReturn(ClassName("a.b.c", "Clazz"))

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "createChannel" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Create a [ManagedChannel] to use with a Clazz.
            |*
            |* Prefer to use the default value with [fromAccessToken], [fromServiceAccount],
            |* or [fromCredentials] unless you need to customize the channel.
            |*/
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun createChannel(
            |    host: kotlin.String = null,
            |    port: kotlin.Int = 443,
            |    enableRetry: kotlin.Boolean = true
            |): io.grpc.ManagedChannel {
            |    val builder = io.grpc.okhttp.OkHttpChannelBuilder.forAddress(host, port)
            |    if (enableRetry) { builder.enableRetry() }
            |    return builder.build()
            |}
            |""".asNormalizedString()
        )
    }
}
