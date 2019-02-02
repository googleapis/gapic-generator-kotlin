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

import com.google.api.kotlin.BaseClientGeneratorTest
import com.google.api.kotlin.ClientPluginOptions
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.api.kotlin.testServiceClientStub
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.AnnotationSpec
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
internal class StubsImplTest {

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
        whenever(ctx.className).doReturn(ClassName("a.b.c", "Foo"))
    }

    @Test
    fun `Generates a stub holder`() {
        whenever(service.name).doReturn("stub")

        val result = StubsImpl().generateHolderType(ctx)

        assertThat(result.toString().asNormalizedString()).isEqualTo(
            """|class Stubs(
               |    val api: com.google.api.kgax.grpc.GrpcClientStub<a.b.c.FooStub>,
               |    val operation: com.google.api.kgax.grpc.GrpcClientStub<com.google.longrunning.OperationsClientStub>
               |) {
               |    interface Factory {
               |        fun create(channel: io.grpc.ManagedChannel, options: com.google.api.kgax.grpc.ClientCallOptions): Stubs
               |    }
               |}""".asNormalizedString()
        )
    }

    @Test
    fun `Generates operation type name`() {
        val result = StubsImpl().getOperationsStubType(ctx)

        assertThat(result).isEqualTo(
            grpcStub(
                ClassName("com.google.longrunning", "OperationsClientStub")
            )
        )
    }

    @Test
    fun `Generates an empty stub`() {
        val by = AnnotationSpec.builder(ATestAnnotation::class).build()

        val result = StubsImpl().generate(ctx, by)

        assertThat(result.types).hasSize(1)

        val stub = result.types.first()
        assertThat(stub.toString().asNormalizedString()).isEqualTo(
            """
            |@com.google.api.kotlin.generator.grpc.ATestAnnotation
            |class FooStub(
            |    channel: io.grpc.Channel,
            |    callOptions: io.grpc.CallOptions = io.grpc.CallOptions.DEFAULT
            |) : io.grpc.stub.AbstractStub<a.b.c.FooStub>(channel, callOptions) {
            |    override fun build(channel: io.grpc.Channel, callOptions: io.grpc.CallOptions): a.b.c.FooStub =
            |        a.b.c.FooStub(channel, callOptions)
            |}
            """.trimIndent().asNormalizedString()
        )
    }
}

// The lite/normal code is almost identical.
// This base class is used to isolate the difference.
internal abstract class StubsImplTestContent(
    invocationOptions: ClientPluginOptions,
    private val marshallerClassName: String
) : BaseClientGeneratorTest(GRPCGenerator(), invocationOptions) {
    private val opts = ServiceOptions(methods = listOf(MethodOptions(name = "Test")))

    @Test
    fun `is annotated`() {
        val stub = generate(opts).testServiceClientStub()

        assertThat(stub.annotationSpecs.first().toString()).isEqualTo(
            "@javax.annotation.Generated(\"com.google.api.kotlin.generator.GRPCGenerator\")"
        )
    }

    @Test
    fun `has a unary testDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "testDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val testDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "Test"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a unary test method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "test" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun test(
            |    request: google.example.TestRequest
            |): com.google.common.util.concurrent.ListenableFuture<google.example.TestResponse> =
            |    futureUnaryCall(channel.newCall(testDescriptor, callOptions), request)
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamTestDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "streamTestDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val streamTestDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "StreamTest"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()
            |        ))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()
            |        ))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamTest method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "streamTest" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun streamTest(
            |    responseObserver: io.grpc.stub.StreamObserver<google.example.TestResponse>
            |): io.grpc.stub.StreamObserver<google.example.TestRequest> =
            |    asyncBidiStreamingCall(channel.newCall(streamTestDescriptor, callOptions), responseObserver)
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamClientTestDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "streamClientTestDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val streamClientTestDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "StreamClientTest"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()
            |        ))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()
            |        ))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamClientTest method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "streamClientTest" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun streamClientTest(
            |    responseObserver: io.grpc.stub.StreamObserver<google.example.TestResponse>
            |): io.grpc.stub.StreamObserver<google.example.TestRequest> =
            |    asyncClientStreamingCall(channel.newCall(streamClientTestDescriptor, callOptions), responseObserver)
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamServerTestDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "streamServerTestDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val streamServerTestDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "StreamServerTest"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()
            |        ))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()
            |        ))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamServerTest method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "streamServerTest" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun streamServerTest(
            |    request: google.example.TestRequest,
            |    responseObserver: io.grpc.stub.StreamObserver<google.example.TestResponse>
            |) = asyncServerStreamingCall(channel.newCall(streamServerTestDescriptor, callOptions), request, responseObserver)
            """.asNormalizedString()
        )
    }
}

internal class FullStubsImplTest :
    StubsImplTestContent(ClientPluginOptions(), "io.grpc.protobuf.ProtoUtils")

internal class LiteStubsImplTest :
    StubsImplTestContent(ClientPluginOptions(lite = true), "io.grpc.protobuf.lite.ProtoLiteUtils")

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
private annotation class ATestAnnotation

private fun grpcStub(type: TypeName) = ClassName(
    GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "GrpcClientStub"
).parameterizedBy(type)
