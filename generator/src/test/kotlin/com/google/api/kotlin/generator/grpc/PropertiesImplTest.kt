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

import com.google.api.kotlin.ClientPluginOptions
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
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
internal class PropertiesImplTest {

    private val stubs: Stubs = mock()
    private val proto: DescriptorProtos.FileDescriptorProto = mock()
    private val service: DescriptorProtos.ServiceDescriptorProto = mock()
    private val meta: Configuration = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(stubs, proto, service, meta, types, ctx)
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.metadata).doReturn(meta)
        whenever(ctx.typeMap).doReturn(types)
        whenever(ctx.commandLineOptions).doReturn(ClientPluginOptions(authGoogleCloud = true))
        whenever(stubs.getStubTypeName(ctx)).doReturn(ClassName("foo.bar", "DaStub"))
    }

    @Test
    fun `Generates client properties`() {
        val result = PropertiesImpl(stubs).generate(ctx)

        assertThat(result).hasSize(3)
        assertThat(result[0].toString().asNormalizedString()).isEqualTo(
            "val channel: io.grpc.ManagedChannel = channel"
        )
        assertThat(result[1].toString().asNormalizedString()).isEqualTo(
            "val options: com.google.api.kgax.grpc.ClientCallOptions = options"
        )
        assertThat(result[2].toString().asNormalizedString()).isEqualTo(
            """
            |private val stubs: Stubs = factory?.create(channel, options) ?: Stubs(
            |    foo.bar.DaStub(channel).prepare(options),
            |    com.google.longrunning.OperationsClientStub(channel).prepare(options)
            |)
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the client constructor`() {
        val result = PropertiesImpl(stubs).generatePrimaryConstructor()

        assertThat(result.toString().asNormalizedString()).isEqualTo(
            """
            |private constructor(
            |    channel: io.grpc.ManagedChannel,
            |    options: com.google.api.kgax.grpc.ClientCallOptions,
            |    factory: Stubs.Factory? = null
            |)
            |""".asNormalizedString()
        )
    }
}
