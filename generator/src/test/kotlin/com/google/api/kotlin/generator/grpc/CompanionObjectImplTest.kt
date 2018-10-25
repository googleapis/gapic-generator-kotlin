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
import com.google.api.kotlin.config.ClientRetry
import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.ServiceOptions
import com.google.common.truth.Truth.assertThat
import com.google.rpc.Code
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

        val prop = type.propertySpecs.first { it.name == "ALL_SCOPES" }
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | * Default scopes to use. Use [prepare] to override as needed.
            | */
            |@kotlin.jvm.JvmStatic
            |val ALL_SCOPES: kotlin.collections.List<kotlin.String> = listOf("a", "b c d e")
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates default retry property`() {
        whenever(ctx.className).doReturn(ClassName("foo.bar", "RetryTest"))

        val type = CompanionObjectImpl().generate(ctx)

        val prop = type.propertySpecs.first { it.name == "RETRY" }
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | * Default operations to retry on failure. Use [prepare] to override as needed.
            | *
            | * Note: This setting controls client side retries. If you enable
            | * server managed retries on the channel do not use this.
            | */
            |@kotlin.jvm.JvmStatic val RETRY: com.google.api.kgax.Retry = com.google.api.kgax.grpc.GrpcBasicRetry(mapOf())
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates default retry property with values`() {
        whenever(ctx.className).doReturn(ClassName("foo.bar", "RetryTest"))
        whenever(serviceOptions.methods).doReturn(listOf(
            MethodOptions("hasRetry", retry = ClientRetry(listOf(Code.ABORTED))),
            MethodOptions("hasNone"),
            MethodOptions("hasSome", retry = ClientRetry(listOf(Code.CANCELLED, Code.DATA_LOSS))),
            MethodOptions("empty", retry = ClientRetry(listOf()))
        ))
        val type = CompanionObjectImpl().generate(ctx)

        val prop = type.propertySpecs.first { it.name == "RETRY" }
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | * Default operations to retry on failure. Use [prepare] to override as needed.
            | *
            | * Note: This setting controls client side retries. If you enable
            | * server managed retries on the channel do not use this.
            | */
            |@kotlin.jvm.JvmStatic val RETRY: com.google.api.kgax.Retry = com.google.api.kgax.grpc.GrpcBasicRetry(mapOf(
            |    "hasRetry" to setOf(Status.Code.ABORTED),
            |    "hasSome" to setOf(Status.Code.CANCELLED, Status.Code.DATA_LOSS)
            ))
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
            |        com.google.api.kgax.grpc.ClientCallOptions(credentials = io.grpc.auth.MoreCallCredentials.from(credentials), retry = RETRY)
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
            |        com.google.api.kgax.grpc.ClientCallOptions(credentials = cred, retry = RETRY)
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
            |        com.google.api.kgax.grpc.ClientCallOptions(credentials = io.grpc.auth.MoreCallCredentials.from(credentials), retry = RETRY)
            |    )
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates factory with environment`() {
        whenever(serviceOptions.scopes).doReturn(listOf("a", "b c d e"))
        whenever(ctx.className).doReturn(ClassName("r.r.r", "Clazz"))

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "fromEnvironment" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | * Create a Clazz from the current system's environment.
            | *
            | * Currently, this method only supports service account credentials that are read from the
            | * path defined by the environment [variableName], which is `CREDENTIALS` by default.
            | *
            | * If a [channel] is not provided one will be created automatically (recommended).
            | */
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun fromEnvironment(
            |    variableName: kotlin.String = "CREDENTIALS",
            |    scopes: kotlin.collections.List<kotlin.String> = ALL_SCOPES,
            |    channel: io.grpc.ManagedChannel? = null
            |): r.r.r.Clazz {
            |    val path = System.getenv(variableName) ?: throw java.lang.IllegalStateException("Credentials environment variable is not set: ${'$'}variableName")
            |    return java.io.File(path).inputStream().use {
            |        r.r.r.Clazz.fromServiceAccount(it, scopes, channel)
            |    }
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
            |    options: com.google.api.kgax.grpc.ClientCallOptions? = null
            |): r.r.r.Clazz = r.r.r.Clazz(
            |    channel ?: createChannel(),
            |    options ?: com.google.api.kgax.grpc.ClientCallOptions(), factory
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
            |*
            |* [enableRetry] can be used to enable server managed retries, which is currently
            |* experimental. You should not use any client retry settings if you enable it.
            |*/
            |@kotlin.jvm.JvmStatic
            |@kotlin.jvm.JvmOverloads
            |fun createChannel(
            |    host: kotlin.String = null,
            |    port: kotlin.Int = 443,
            |    enableRetry: kotlin.Boolean = false
            |): io.grpc.ManagedChannel {
            |    val builder = io.grpc.ManagedChannelBuilder.forAddress(host, port)
            |    if (enableRetry) { builder.enableRetry() }
            |    return builder.build()
            |}
            |""".asNormalizedString()
        )
    }
}
