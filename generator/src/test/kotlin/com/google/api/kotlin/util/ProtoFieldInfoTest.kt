/*
 * Copyright 2019 Google LLC
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

import com.google.api.kotlin.BaseClientGeneratorTest
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.config.PropertyPath
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.Test

internal class ProtoFieldInfoTest : BaseClientGeneratorTest("test", "TestServiceClient") {

    @Test
    fun `can find service level comments`() {
        val service = services.find { it.name == "TestService" }!!
        val method = service.methodList.find { it.name == "Test" }!!

        val comments = proto.getMethodComments(service, method)

        assertThat(comments?.trim()).isEqualTo("This is the test method")
    }

    @Test
    fun `can find parameter comments`() {
        val message = proto.messageTypeList.find { it.name == "TestRequest" }!!
        val field = message.fieldList.find { it.name == "query" }!!
        val kotlinType = ClassName("a", "Foo")

        val fieldInfo =
            ProtoFieldInfo(proto, message, field, -1, kotlinType)
        val comment = fieldInfo.file.getParameterComments(fieldInfo)

        assertThat(comment?.trim()).isEqualTo("the query")
    }

    @Test
    fun `can identity ints and longs`() {
        listOf(
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64
        ).forEach { type ->
            val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setType(type)
                .build()
            assertThat(proto.isIntOrLong()).isTrue()
        }

        listOf(
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_GROUP
        ).forEach { type ->
            val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setType(type)
                .build()
            assertThat(proto.isIntOrLong()).isFalse()
        }
    }

    @Test
    fun `can identify a String`() {
        val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
            .build()
        assertThat(proto.asClassName(mock())).isEqualTo(String::class.asTypeName())
    }

    @Test
    fun `can identify a Boolean`() {
        val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL)
            .build()
        assertThat(proto.asClassName(mock())).isEqualTo(Boolean::class.asTypeName())
    }

    @Test
    fun `can identify a Double`() {
        val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE)
            .build()
        assertThat(proto.asClassName(mock())).isEqualTo(Double::class.asTypeName())
    }

    @Test
    fun `can identify a Float`() {
        val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT)
            .build()
        assertThat(proto.asClassName(mock())).isEqualTo(Float::class.asTypeName())
    }

    @Test
    fun `can identify an Integer`() {
        listOf(
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32
        ).forEach { type ->
            val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setType(type)
                .build()
            assertThat(proto.asClassName(mock())).isEqualTo(Int::class.asTypeName())
        }
    }

    @Test
    fun `can identify a Long`() {
        listOf(
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64
        ).forEach { type ->
            val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setType(type)
                .build()
            assertThat(proto.asClassName(mock())).isEqualTo(Long::class.asTypeName())
        }
    }

    @Test
    fun `can identify a ByteString`() {
        val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES)
            .build()
        assertThat(proto.asClassName(mock())).isEqualTo(ByteString::class.asTypeName())
    }

    @Test
    fun `can identify a type`() {
        val type: ClassName = mock()
        val typeMap: ProtobufTypeMapper = mock {
            on { getKotlinType(any()) } doReturn type
        }
        val proto = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("my.type")
            .build()

        assertThat(proto.asClassName(typeMap)).isEqualTo(type)
        verify(typeMap).getKotlinType(eq("my.type"))
    }

    @Test
    fun `can describe maps`() {
        val keyType: DescriptorProtos.FieldDescriptorProto = mock {
            on { name } doReturn "key"
        }
        val valueType: DescriptorProtos.FieldDescriptorProto = mock {
            on { name } doReturn "value"
        }
        val mapType: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(keyType, valueType)
        }
        val typeMap: ProtobufTypeMapper = mock {
            on { getProtoTypeDescriptor(eq("the.type")) } doReturn mapType
        }

        val field = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("the.type")
            .build()

        val (key, value) = field.describeMap(typeMap)
        assertThat(key).isEqualTo(keyType)
        assertThat(value).isEqualTo(valueType)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on invalid map type`() {
        val keyType: DescriptorProtos.FieldDescriptorProto = mock {
            on { name } doReturn "key"
        }
        val valueType: DescriptorProtos.FieldDescriptorProto = mock {
            on { name } doReturn "nope"
        }
        val mapType: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(keyType, valueType)
        }
        val typeMap: ProtobufTypeMapper = mock {
            on { getProtoTypeDescriptor(eq("the.type")) } doReturn mapType
        }

        val field = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("the.type")
            .build()

        field.describeMap(typeMap)
    }

    @Test
    fun `can identify a LRO`() {
        assertThat(
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setInputType(".google.longrunning.Operation")
                .build().isLongRunningOperation()
        ).isFalse()
        assertThat(
            DescriptorProtos.MethodDescriptorProto.newBuilder()
                .setOutputType(".google.longrunning.Operation")
                .build().isLongRunningOperation()
        ).isTrue()
        listOf(
            "type", "longrunning.Operation", ".com.longrunning.Operation", "Operation", "long_running_operation"
        ).forEach { type ->
            assertThat(
                DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setInputType(type)
                    .build().isLongRunningOperation()
            ).isFalse()
            assertThat(
                DescriptorProtos.MethodDescriptorProto.newBuilder()
                    .setOutputType(type)
                    .build().isLongRunningOperation()
            ).isFalse()
        }
    }

    @Test
    fun `can get field info for a simple path`() {
        val proto: DescriptorProtos.FileDescriptorProto = mock()
        val className: ClassName = mock()
        val typeMap: ProtobufTypeMapper = mock {
            on { getKotlinType(any()) } doReturn className
        }

        listOf(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("prop")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName("property")
                .build(),
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("prop")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL)
                .build()
        ).forEach { propField ->
            val context: GeneratorContext = mock {
                on { this.typeMap } doReturn typeMap
                on { this.proto } doReturn proto
            }
            val type: DescriptorProtos.DescriptorProto = mock {
                on { fieldList } doReturn listOf(propField)
            }

            val path = PropertyPath("prop")
            val result = getProtoFieldInfoForPath(context, path, type)

            assertThat(result.field).isEqualTo(propField)
            assertThat(result.file).isEqualTo(proto)
            assertThat(result.index).isEqualTo(-1)
            assertThat(result.message).isEqualTo(type)
            if (propField.hasTypeName()) {
                assertThat(result.kotlinType).isEqualTo(className)
            } else {
                assertThat(result.kotlinType).isEqualTo(Boolean::class.asClassName())
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on an invalid path`() {
        val proto: DescriptorProtos.FileDescriptorProto = mock()
        val typeMap: ProtobufTypeMapper = mock()
        val context: GeneratorContext = mock {
            on { this.typeMap } doReturn typeMap
            on { this.proto } doReturn proto
        }
        val type: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf<DescriptorProtos.FieldDescriptorProto>()
        }

        val path = PropertyPath("prop")
        getProtoFieldInfoForPath(context, path, type)
    }

    @Test
    fun `can get field info for a simple 0-indexed path`() = testSimpleIndexedPath("prop[0]")

    @Test(expected = IllegalArgumentException::class)
    fun `throws on a non-0-indexed path`() = testSimpleIndexedPath("prop[2]")

    private fun testSimpleIndexedPath(name: String) {
        val proto: DescriptorProtos.FileDescriptorProto = mock()
        val className: ClassName = mock()
        val typeMap: ProtobufTypeMapper = mock {
            on { getKotlinType(any()) } doReturn className
        }

        val propField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("prop")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("property")
            .build()
        val context: GeneratorContext = mock {
            on { this.typeMap } doReturn typeMap
            on { this.proto } doReturn proto
        }
        val type: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(propField)
        }

        val result = getProtoFieldInfoForPath(context, PropertyPath(name), type)

        assertThat(result.field).isEqualTo(propField)
        assertThat(result.file).isEqualTo(proto)
        assertThat(result.index).isEqualTo(0)
        assertThat(result.message).isEqualTo(type)
        assertThat(result.kotlinType).isEqualTo(className)
    }

    @Test
    fun `can get field info for a compound path`() {
        val proto: DescriptorProtos.FileDescriptorProto = mock()
        val className: ClassName = mock()

        val firstField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("first")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("secondType")
            .build()
        val firstFieldProto: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(firstField)
        }
        val secondField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("second")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("thirdType")
            .build()
        val secondFiledProto: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(secondField)
        }
        val thirdField = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("third")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName("someType")
            .build()
        val thirdFiledProto: DescriptorProtos.DescriptorProto = mock {
            on { fieldList } doReturn listOf(thirdField)
        }

        val typeMap: ProtobufTypeMapper = mock {
            on { getKotlinType(any()) } doReturn className
            on { hasProtoTypeDescriptor(eq("firstType")) } doReturn true
            on { hasProtoTypeDescriptor(eq("secondType")) } doReturn true
            on { hasProtoTypeDescriptor(eq("thirdType")) } doReturn true
            on { getProtoTypeDescriptor(eq("firstType")) } doReturn firstFieldProto
            on { getProtoTypeDescriptor(eq("secondType")) } doReturn secondFiledProto
            on { getProtoTypeDescriptor(eq("thirdType")) } doReturn thirdFiledProto
        }

        val context: GeneratorContext = mock {
            on { this.typeMap } doReturn typeMap
            on { this.proto } doReturn proto
        }

        val path = PropertyPath(listOf("first", "second", "third"))
        val result = getProtoFieldInfoForPath(context, path, firstFieldProto)

        assertThat(result.field).isEqualTo(thirdField)
        assertThat(result.file).isEqualTo(proto)
        assertThat(result.index).isEqualTo(-1)
        assertThat(result.message).isEqualTo(thirdFiledProto)
        assertThat(result.kotlinType).isEqualTo(className)
    }
}