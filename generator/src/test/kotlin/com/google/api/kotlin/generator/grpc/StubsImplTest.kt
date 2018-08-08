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
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlin.test.BeforeTest
import kotlin.test.Test

class StubsImplTest {

    private val proto: DescriptorProtos.FileDescriptorProto = mock()
    private val service: DescriptorProtos.ServiceDescriptorProto = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(proto, service, types, ctx)
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.typeMap).doReturn(types)
    }

    @Test
    fun `Generates a stub holder`() {
        whenever(service.name).doReturn("stub")
        whenever(
            types.getKotlinGrpcTypeInnerClass(
                eq(proto), eq(service), any(), eq("stubFutureStub")
            )
        ).doReturn(ClassName("one", "Future"))
        whenever(
            types.getKotlinGrpcTypeInnerClass(
                eq(proto), eq(service), any(), eq("stubStub")
            )
        ).doReturn(ClassName("two", "Stream"))

        val result = StubsImpl().generateHolderType(ctx)

        assertThat(result.toString().asNormalizedString()).isEqualTo(
            """|class Stubs(
               |    val stream: com.google.kgax.grpc.GrpcClientStub<two.Stream>,
               |    val future: com.google.kgax.grpc.GrpcClientStub<one.Future>,
               |    val operation: com.google.kgax.grpc.GrpcClientStub<com.google.longrunning.OperationsGrpc.OperationsFutureStub>
               |) {
               |    interface Factory {
               |        fun create(channel: io.grpc.ManagedChannel, options: com.google.kgax.grpc.ClientCallOptions): Stubs
               |    }
               |}""".asNormalizedString()
        )
    }

    @Test
    fun `Generates future type name`() {
        whenever(service.name).doReturn("a_service")
        whenever(
            types.getKotlinGrpcTypeInnerClass(
                eq(proto), eq(service), eq("Grpc"), eq("a_serviceFutureStub")
            )
        ).doReturn(ClassName("baz.bar", "AName"))

        val result = StubsImpl().getFutureStubType(ctx)

        assertThat(result).isEqualTo(grpcStub(ClassName("baz.bar", "AName")))
    }

    @Test
    fun `Generates stream type name`() {
        whenever(service.name).doReturn("Someservice")
        whenever(
            types.getKotlinGrpcTypeInnerClass(
                eq(proto), eq(service), eq("Grpc"), eq("SomeserviceStub")
            )
        ).doReturn(ClassName("baz.bar.wow", "name"))

        val result = StubsImpl().getStreamStubType(ctx)

        assertThat(result).isEqualTo(grpcStub(ClassName("baz.bar.wow", "name")))
    }

    @Test
    fun `Generates operation type name`() {
        val result = StubsImpl().getOperationsStubType(ctx)

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
