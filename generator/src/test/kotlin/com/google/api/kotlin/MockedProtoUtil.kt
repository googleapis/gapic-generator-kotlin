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

package com.google.api.kotlin

import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.TypeNamePair
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.ClassName

// generates a simple mocked test proto with a primitive, message, repeated, and mapped type.
internal class MockedProtoUtil {

    class BasicMockedProto(val typeMap: ProtobufTypeMapper = mock()) {
        val fieldBar = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("bar")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL)
            .build()
        val fieldFoo = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("foo")
            .setTypeName(".test.bar")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .build()
        val fieldStr = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("str")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
            .build()
        val fieldMap = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("pam")
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
            .setTypeName(".test.my.map")
            .build()
    }

    companion object {
        fun getBasicMockedProto(): BasicMockedProto {
            var proto = BasicMockedProto()

            val typeOptions: DescriptorProtos.MessageOptions = mock {
                on { mapEntry } doReturn false
            }
            val mapOptions: DescriptorProtos.MessageOptions = mock {
                on { mapEntry } doReturn true
            }
            val protoType: DescriptorProtos.DescriptorProto = mock {
                on { fieldList } doReturn listOf(proto.fieldStr, proto.fieldFoo, proto.fieldMap)
                on { options } doReturn typeOptions
            }
            val barType: DescriptorProtos.DescriptorProto = mock {
                on { fieldList } doReturn listOf(proto.fieldBar)
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
            whenever(proto.typeMap.getProtoTypeDescriptor(eq(".test.the.input"))).doReturn(protoType)
            whenever(proto.typeMap.getProtoTypeDescriptor(eq(".test.bar"))).doReturn(barType)
            whenever(proto.typeMap.getProtoTypeDescriptor(eq(".test.my.map"))).doReturn(mapType)
            whenever(proto.typeMap.getKotlinType(eq(".test.the.input"))).doReturn(ClassName("test.the", "Input"))
            whenever(proto.typeMap.getKotlinType(eq(".test.bar"))).doReturn(ClassName("test", "Bar"))
            whenever(proto.typeMap.getKotlinType(eq(".test.my.map"))).doReturn(ClassName("test.my", "Map"))
            whenever(proto.typeMap.hasProtoTypeDescriptor(".test.bar")).doReturn(true)
            whenever(proto.typeMap.getAllTypes()).doReturn(
                listOf(
                    TypeNamePair(".test.the.input", "test.the.Input"),
                    TypeNamePair(".test.bar", "test.Bar"),
                    TypeNamePair(".test.my.map", "test.my.Map")
                )
            )

            return proto
        }
    }
}
