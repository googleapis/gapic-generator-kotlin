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
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests basic functionality.
 *
 * more complex tests use the test protos in [GRPCGeneratorTest].
 */
class StubsImplTest {

    private val baseClassGenerator: BaseClass = mock()
    private val proto: DescriptorProtos.FileDescriptorProto = mock()
    private val service: DescriptorProtos.ServiceDescriptorProto = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(baseClassGenerator, proto, service, types, ctx)
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.typeMap).doReturn(types)
        whenever(baseClassGenerator.stubTypeName(ctx)).doReturn(ClassName("a.b.c", "Foo"))
    }

    @Test
    fun `Generates a stub holder`() {
        whenever(service.name).doReturn("stub")

        val result = StubsImpl(baseClassGenerator).generateHolderType(ctx)

        assertThat(result.toString().asNormalizedString()).isEqualTo(
            """|class Stubs(
               |    val api: com.google.kgax.grpc.GrpcClientStub<a.b.c.Foo>,
               |    val operation: com.google.kgax.grpc.GrpcClientStub<com.google.longrunning.OperationsGrpc.OperationsFutureStub>
               |) {
               |    interface Factory {
               |        fun create(channel: io.grpc.ManagedChannel, options: com.google.kgax.grpc.ClientCallOptions): Stubs
               |    }
               |}""".asNormalizedString()
        )
    }

    @Test
    fun `Generates operation type name`() {
        val result = StubsImpl(baseClassGenerator).getOperationsStubType(ctx)

        assertThat(result).isEqualTo(
            grpcStub(
                ClassName("com.google.longrunning.OperationsGrpc", "OperationsFutureStub")
            )
        )
    }

    private fun grpcStub(type: TypeName) = ClassName(
        GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "GrpcClientStub"
    ).parameterizedBy(type)
}
