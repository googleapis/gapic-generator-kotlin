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

import com.google.api.kotlin.DescriptorProto
import com.google.api.kotlin.FieldDescriptorProto
import com.google.api.kotlin.FileDescriptorProto
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.MethodDescriptorProto
import com.google.api.kotlin.ServiceDescriptorProto
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.util.ProtoFieldInfo
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests basic functionality.
 *
 * more complex tests use the test protos in [GRPCGeneratorTest].
 */
internal class UnitTestImplTest {

    private val documentationGenerator: Documentation = mock()
    private val stubGenerator: StubsImpl = mock()
    private val proto: DescriptorProtos.FileDescriptorProto = mock()
    private val service: DescriptorProtos.ServiceDescriptorProto = mock()
    private val meta: Configuration = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(documentationGenerator, stubGenerator, proto, service, meta, types, ctx)
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.typeMap).doReturn(types)
        whenever(ctx.metadata).doReturn(meta)
    }

    @Test
    fun `Generates the prepare fun`() {
        whenever(stubGenerator.getApiStubType(ctx)).doReturn(
            ClassName("the.stub", "Stubby").parameterizedBy(ClassName("a.b", "C"))
        )
        whenever(types.getKotlinType(".foo.bar.ZaInput")).doReturn(
            ClassName("foo.bar", "ZaInput")
        )
        whenever(types.getKotlinType(".foo.bar.ZaOutput")).doReturn(
            ClassName("foo.bar", "ZaOutput")
        )
        whenever(types.getProtoTypeDescriptor(any())).doReturn(DescriptorProto {
            addField(FieldDescriptorProto {
                name = "da_field"
                type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING
            })
        })
        whenever(
            documentationGenerator.generateMethodKDoc(
                eq(ctx), any(), any(), any(), anyOrNull(), any()
            )
        ).doReturn(CodeBlock.of("some docs"))

        val opts: ServiceOptions = mock()
        whenever(meta[any<String>()]).doReturn(opts)
        whenever(meta[any<DescriptorProtos.ServiceDescriptorProto>()]).doReturn(opts)

        whenever(ctx.className).doReturn(ClassName("foo.bar", "ZaTest"))
        whenever(ctx.proto).doReturn(
            FileDescriptorProto {
                name = "my-file"
            })
        whenever(ctx.service).doReturn(
            ServiceDescriptorProto {
                addMethod(MethodDescriptorProto {
                    name = "FunFunction"
                    inputType = ".foo.bar.ZaInput"
                    outputType = ".foo.bar.ZaOutput"
                })
            })

        val result =
            FunctionsImpl(documentationGenerator, UnitTestImpl(stubGenerator)).generate(ctx)

        val prepareFun = result.first { it.function.name == "prepare" }
        Truth.assertThat(prepareFun.unitTestCode).isNull()

        assertThat(prepareFun.function.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | * Prepare for an API call by setting any desired options. For example:
            | *
            | *
            | ```
            | * val client = foo.bar.ZaTest.fromServiceAccount(YOUR_KEY_FILE)
            | * val response = client.prepare {
            | *     withMetadata("my-custom-header", listOf("some", "thing"))
            | * }.funFunction(request).get()
            | * ```
            | *
            | * You may save the client returned by this call and reuse it if you
            | * plan to make multiple requests with the same settings.
            | */
            |fun prepare(init: com.google.kgax.grpc.ClientCallOptions.Builder.() -> kotlin.Unit): foo.bar.ZaTest {
            |    val options = com.google.kgax.grpc.ClientCallOptions.Builder(options)
            |    options.init()
            |    return foo.bar.ZaTest(channel, options.build())
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `can generate sensible message mocks`() {
        whenever(types.hasProtoEnumDescriptor(any())).doReturn(false)
        whenever(types.getProtoTypeDescriptor(eq(".my.test.type"))).doReturn(
            DescriptorProto {
                options = DescriptorProtos.MessageOptions.newBuilder().setMapEntry(false).build()
            }
        )

        val mockMaker = UnitTestImpl.DefaultMockMaker

        assertThat(mockMaker.getMockValue(types, null)).isEqualTo("mock()")

        val fieldInfo: ProtoFieldInfo = mock {
            on { field } doReturn FieldDescriptorProto {
                type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE
                typeName = ".my.test.type"
            }
        }
        assertThat(mockMaker.getMockValue(types, fieldInfo)).isEqualTo("mock()")
    }

    @Test
    fun `can generate sensible enum values`() {
        val enumDescriptor: DescriptorProtos.EnumDescriptorProto = mock {
            on { valueList } doReturn listOf(
                DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                    .setName("THE_ONE")
                    .build(),
                DescriptorProtos.EnumValueDescriptorProto.newBuilder()
                    .setName("TWO")
                    .build()
            )
        }
        whenever(types.hasProtoEnumDescriptor(eq(".my.test.type"))).doReturn(true)
        whenever(types.getProtoEnumDescriptor(eq(".my.test.type"))).doReturn(enumDescriptor)
        whenever(types.getKotlinType(eq(".my.test.type"))).doReturn(ClassName("my.test", "Type"))

        val mockMaker = UnitTestImpl.DefaultMockMaker

        val fieldInfo: ProtoFieldInfo = mock {
            on { field } doReturn FieldDescriptorProto { typeName = ".my.test.type" }
        }
        assertThat(mockMaker.getMockValue(types, fieldInfo)).isEqualTo("Type.THE_ONE")
    }

    @Test
    fun `can generate sensible primitive values`() {
        whenever(types.hasProtoEnumDescriptor(any())).doReturn(false)

        val mockMaker = UnitTestImpl.DefaultMockMaker

        assertMockValueForPrimitive(
            mockMaker,
            "\"hi there!\"",
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING }
        )

        assertMockValueForPrimitive(
            mockMaker,
            "true",
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL }
        )

        assertMockValueForPrimitive(
            mockMaker,
            "2.0",
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE }
        )

        assertMockValueForPrimitive(
            mockMaker,
            "4.0",
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT }
        )

        assertMockValueForPrimitive(
            mockMaker,
            "2",
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32 }
        )

        assertMockValueForPrimitive(
            mockMaker,
            "400L",
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64 },
            { type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64 }
        )
    }

    private fun assertMockValueForPrimitive(
        mockMaker: MockMaker,
        expectedValue: String,
        vararg builders: DescriptorProtos.FieldDescriptorProto.Builder.() -> Unit
    ) {
        for (builder in builders) {
            val descriptor = DescriptorProtos.FieldDescriptorProto.newBuilder()
            descriptor.apply(builder)
            assertThat(mockMaker.getMockValue(types, mock {
                on { field } doReturn descriptor.build()
            })).isEqualTo(expectedValue)
        }
    }
}
