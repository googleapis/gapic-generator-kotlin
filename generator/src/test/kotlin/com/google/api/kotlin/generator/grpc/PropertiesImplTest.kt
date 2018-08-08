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
import com.google.api.kotlin.config.ConfigurationMetadata
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

class PropertiesImplTest {

    private val proto: DescriptorProtos.FileDescriptorProto = mock()
    private val service: DescriptorProtos.ServiceDescriptorProto = mock()
    private val meta: ConfigurationMetadata = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(proto, service, meta, types, ctx)
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.metadata).doReturn(meta)
        whenever(ctx.typeMap).doReturn(types)
    }

    @Test
    fun `Generates client properties`() {
        whenever(
            types.getKotlinGrpcType(
                ctx.proto, ctx.service, "Grpc"
            )
        ).doReturn(ClassName("q.w.e.r.t.y", "Key"))

        val result = PropertiesImpl().generate(ctx)

        assertThat(result).hasSize(1)
        assertThat(result.first().toString().asNormalizedString()).isEqualTo(
            """
            |private val stubs: Stubs = factory?.create(channel, options) ?: Stubs(
            |    q.w.e.r.t.y.Key.newStub(channel).prepare(options),
            |    q.w.e.r.t.y.Key.newFutureStub(channel).prepare(options),
            |    com.google.longrunning.OperationsGrpc.newFutureStub(channel).prepare(options)
            |)
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the client constructor`() {
        val result = PropertiesImpl().generatePrimaryConstructor()

        assertThat(result.toString().asNormalizedString()).isEqualTo(
            """
            |private constructor(
            |    channel: io.grpc.ManagedChannel,
            |    options: com.google.kgax.grpc.ClientCallOptions,
            |    factory: Stubs.Factory? = null
            |)
            |""".asNormalizedString()
        )
    }
}
