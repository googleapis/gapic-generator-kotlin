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

package com.google.api.kotlin.util

import com.google.api.kotlin.MockedProtoUtil
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.asPropertyPath
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class FlatteningTest {

    val context: GeneratorContext = mock()
    val proto: DescriptorProtos.FileDescriptorProto = mock()

    lateinit var mocked: MockedProtoUtil.BasicMockedProto

    @BeforeTest
    fun before() {
        reset(context, proto)

        mocked = MockedProtoUtil.getBasicMockedProto()
        whenever(context.typeMap).doReturn(mocked.typeMap)
        whenever(context.proto).doReturn(proto)
    }

    @Test
    fun `can get parameters of flatten a method`() {
        val method = DescriptorProtos.MethodDescriptorProto.newBuilder()
            .setInputType(".test.the.input")
            .build()
        val config = FlattenedMethod(
            listOf(
                "foo.bar".asPropertyPath(),
                "str".asPropertyPath(),
                "pam".asPropertyPath()
            )
        )

        val params = Flattening.getFlattenedParameters(context, method, config)

        assertThat(params.config).isEqualTo(config)
        assertThat(params.parameters).hasSize(3)

        val bar = params.parameters.first { it.spec.name == "bar" }
        assertThat(bar.spec.name).isEqualTo("bar")
        assertThat(bar.spec.type).isEqualTo(Boolean::class.asTypeName())
        assertThat(bar.flattenedPath).isEqualTo("foo.bar".asPropertyPath())
        assertThat(bar.flattenedFieldInfo?.kotlinType).isEqualTo(Boolean::class.asTypeName())
        assertThat(bar.flattenedFieldInfo?.file).isEqualTo(proto)
        assertThat(bar.flattenedFieldInfo?.field).isEqualTo(mocked.fieldBar)

        val str = params.parameters.first { it.spec.name == "str" }
        assertThat(str.spec.name).isEqualTo("str")
        assertThat(str.spec.type).isEqualTo(String::class.asTypeName())
        assertThat(str.flattenedPath).isEqualTo("str".asPropertyPath())
        assertThat(str.flattenedFieldInfo?.kotlinType).isEqualTo(String::class.asTypeName())
        assertThat(str.flattenedFieldInfo?.file).isEqualTo(proto)
        assertThat(str.flattenedFieldInfo?.field).isEqualTo(mocked.fieldStr)

        val pam = params.parameters.first { it.spec.name == "pam" }
        assertThat(pam.spec.name).isEqualTo("pam")
        assertThat(pam.spec.type).isEqualTo(
            Map::class.asClassName().parameterizedBy(
                String::class.asTypeName(), String::class.asTypeName()
            )
        )
        assertThat(pam.flattenedPath).isEqualTo("pam".asPropertyPath())
        assertThat(pam.flattenedFieldInfo?.kotlinType).isEqualTo(ClassName("test.my", "Map"))
        assertThat(pam.flattenedFieldInfo?.file).isEqualTo(proto)
        assertThat(pam.flattenedFieldInfo?.field).isEqualTo(mocked.fieldMap)

        assertThat(params.requestObject.asNormalizedString()).isEqualTo(
            """
            |test.the.input {
            |    this.str = str
            |    this.pam = pam
            |    foo = test.bar {
            |        this.bar = bar
            |    }
            |}
            """.trimIndent().asNormalizedString()
        )
    }
}