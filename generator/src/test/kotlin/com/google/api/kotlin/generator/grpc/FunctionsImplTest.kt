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
import com.google.api.kotlin.config.ConfigurationMetadata
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import kotlin.test.BeforeTest
import kotlin.test.Test

// tests basic functionality
// more complex tests use the test protos in [GRPCGeneratorTest].
class FunctionsImplTest {

    private val unitTestGenerator: UnitTest = mock()
    private val proto: DescriptorProtos.FileDescriptorProto = mock()
    private val service: DescriptorProtos.ServiceDescriptorProto = mock()
    private val meta: ConfigurationMetadata = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(unitTestGenerator, meta, types, service, types, ctx)
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.typeMap).doReturn(types)
        whenever(ctx.metadata).doReturn(meta)
    }

    @Test
    fun `Generates the prepare fun`() {
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

        val opts: ServiceOptions = mock()
        whenever(meta.scopesAsLiteral).doReturn("\"a\", \"b c d e\"")
        whenever(meta.get(any<String>())).doReturn(opts)
        whenever(meta.get(any<DescriptorProtos.ServiceDescriptorProto>())).doReturn(opts)

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

        val result = FunctionsImpl(unitTestGenerator).generate(ctx)

        val prepareFun = result.first { it.function.name == "prepare" }
        assertThat(prepareFun.function.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Prepare for an API call by setting any desired options. For example:
            |*
            |* ```
            |* val client = foo.bar.ZaTest.fromServiceAccount(< keyfile >)
            |* val response = client.prepare {
            |*   withMetadata("my-custom-header", listOf("some", "thing"))
            |* }.funFunction(request).get()
            |* ```
            |*
            |* You may save the client returned by this call and reuse it if you
            |* plan to make multiple requests with the same settings.
            |*/
            |fun prepare(init: com.google.kgax.grpc.ClientCallOptions.Builder.() -> kotlin.Unit): foo.bar.ZaTest {
            |    val options = com.google.kgax.grpc.ClientCallOptions.Builder(options)
            |    options.init()
            |    return foo.bar.ZaTest(channel, options.build())
            |}
            |""".asNormalizedString()
        )
    }
}
