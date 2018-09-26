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

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.asPropertyPath
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
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
    val typeMap: ProtobufTypeMapper = mock()

    @BeforeTest
    fun before() {
        reset(context, proto, typeMap)

        whenever(context.typeMap).doReturn(typeMap)
        whenever(context.proto).doReturn(proto)
    }

    @Test
    fun `can get parameters of flatten a method`() {
        val barField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("bar")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL)
            .build()
        val fooField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("foo")
            .setTypeName(".test.bar")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .build()
        val strField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("str")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
            .build()
        val mapField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("pam")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".test.my.map")
            .build()

        val typeOptions: DescriptorProtos.MessageOptions = mock {
            on { mapEntry } doReturn false
        }
        val mapOptions: DescriptorProtos.MessageOptions = mock {
            on { mapEntry } doReturn true
        }
        val protoType: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(strField, fooField, mapField)
            on { options } doReturn typeOptions
        }
        val barType: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(barField)
            on { options } doReturn typeOptions
        }
        val mapType: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName("key")
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                    .build(),
                DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName("value")
                    .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                    .build()
            )
            on { options } doReturn mapOptions
        }
        whenever(typeMap.getProtoTypeDescriptor(eq(".test.the.input"))).doReturn(protoType)
        whenever(typeMap.getProtoTypeDescriptor(eq(".test.bar"))).doReturn(barType)
        whenever(typeMap.getProtoTypeDescriptor(eq(".test.my.map"))).doReturn(mapType)
        whenever(typeMap.getKotlinType(eq(".test.the.input"))).doReturn(ClassName("test.the", "Input"))
        whenever(typeMap.getKotlinType(eq(".test.bar"))).doReturn(ClassName("test", "Bar"))
        whenever(typeMap.getKotlinType(eq(".test.my.map"))).doReturn(ClassName("test.my", "Map"))
        whenever(typeMap.hasProtoTypeDescriptor(".test.bar")).doReturn(true)

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
        assertThat(bar.flattenedFieldInfo?.field).isEqualTo(barField)

        val str = params.parameters.first { it.spec.name == "str" }
        assertThat(str.spec.name).isEqualTo("str")
        assertThat(str.spec.type).isEqualTo(String::class.asTypeName())
        assertThat(str.flattenedPath).isEqualTo("str".asPropertyPath())
        assertThat(str.flattenedFieldInfo?.kotlinType).isEqualTo(String::class.asTypeName())
        assertThat(str.flattenedFieldInfo?.file).isEqualTo(proto)
        assertThat(str.flattenedFieldInfo?.field).isEqualTo(strField)

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
        assertThat(pam.flattenedFieldInfo?.field).isEqualTo(mapField)

        assertThat(params.requestObject.asNormalizedString()).isEqualTo(
            """
            |test.the.Input {
            |    this.str = str
            |    putAllPam(pam)
            |    foo = test.Bar {
            |        this.bar = bar
            |    }
            |}
            """.trimIndent().asNormalizedString()
        )
    }
}