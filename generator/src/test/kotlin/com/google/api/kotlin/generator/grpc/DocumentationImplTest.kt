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
import com.google.api.kotlin.config.BrandingOptions
import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.ProtobufExtensionRegistry
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.SampleMethod
import com.google.api.kotlin.config.SampleParameterAndValue
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.config.asPropertyPath
import com.google.api.kotlin.util.ParameterInfo
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.compiler.PluginProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class DocumentationImplTest {

    // accessors for the test protos
    private val generatorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(
        javaClass.getResourceAsStream("/generated-simple.data"),
        ProtobufExtensionRegistry.INSTANCE
    )

    private val simpleMethod = generatorRequest
        .protoFileList.first()
        .serviceList.first()
        .methodList.first()

    private val proto = generatorRequest.protoFileList.first()
    private val service = proto.serviceList.first()

    private val meta: Configuration = mock()
    private val options: ServiceOptions = mock()
    private val types: ProtobufTypeMapper = mock()
    private val ctx: GeneratorContext = mock()

    @BeforeTest
    fun before() {
        reset(meta, options, types, ctx)
        whenever(ctx.className).doReturn(ClassName("doc", "test"))
        whenever(ctx.proto).doReturn(proto)
        whenever(ctx.service).doReturn(service)
        whenever(ctx.typeMap).doReturn(types)
        whenever(ctx.metadata).doReturn(meta)
        whenever(ctx.metadata[any<String>()]).doReturn(options)
        whenever(options.methods).doReturn(listOf<MethodOptions>())
        whenever(ctx.commandLineOptions).doReturn(ClientPluginOptions(authGoogleCloud = true))
    }

    @Test
    fun `generates class level documentation`() {
        whenever(meta.branding).doReturn(
            BrandingOptions(
                name = "some name",
                summary = "a little summary",
                url = "http://this-thing.example.com"
            )
        )

        val doc = DocumentationImpl().generateClassKDoc(ctx)

        assertThat(doc.asNormalizedString()).isEqualTo(
            """
            |some name
            |
            |a little summary
            |
            |[Product Documentation](http://this-thing.example.com)
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates method level documentation`() {
        whenever(types.getKotlinType(any())).doReturn(ClassName("a.test", "type"))
        whenever(types.getProtoTypeDescriptor(any())).doReturn(proto.messageTypeList.first { it.name == "SimpleRequest" })

        val doc = DocumentationImpl().generateMethodKDoc(
            context = ctx,
            method = simpleMethod,
            methodOptions = MethodOptions("myMethod")
        )

        assertThat(doc.asNormalizedString()).isEqualTo(
            """
            |so simple!
            |
            |For example:
            |```
            |val client = test.fromServiceAccount(YOUR_KEY_FILE)
            |val result = client.myMethod(a.test.type { })
            |```
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates method level documentation with flattening`() {
        whenever(types.getKotlinType(any())).doReturn(ClassName("a.test", "type"))
        whenever(types.getProtoTypeDescriptor(any())).doReturn(proto.messageTypeList.first { it.name == "SimpleRequest" })

        val doc = DocumentationImpl().generateMethodKDoc(
            context = ctx,
            method = simpleMethod,
            methodOptions = MethodOptions("myMethod"),
            flatteningConfig = FlattenedMethod(listOf("query".asPropertyPath())),
            parameters = listOf(ParameterInfo(
                ParameterSpec.builder("query", String::class.asTypeName()).build(),
                "query".asPropertyPath())
            )
        )

        assertThat(doc.asNormalizedString()).isEqualTo(
            """
            |so simple!
            |
            |For example:
            |```
            |val client = test.fromServiceAccount(YOUR_KEY_FILE)
            |val result = client.myMethod(query)
            |```
            |
            |@param query
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates method level documentation with a sample`() {
        whenever(types.getKotlinType(any())).doReturn(ClassName("a.test", "type"))
        whenever(types.getProtoTypeDescriptor(any())).doReturn(proto.messageTypeList.first { it.name == "SimpleRequest" })

        val doc = DocumentationImpl().generateMethodKDoc(
            context = ctx,
            method = simpleMethod,
            methodOptions = MethodOptions("myMethod", samples = listOf(SampleMethod(listOf(SampleParameterAndValue("query", "4")))))
        )

        assertThat(doc.asNormalizedString()).isEqualTo(
            """
            |so simple!
            |
            |For example:
            |```
            |val client = test.fromServiceAccount(YOUR_KEY_FILE)
            |val result = client.myMethod(a.test.type {
            |    query = 4
            })
            |```
            """.asNormalizedString()
        )
    }
}