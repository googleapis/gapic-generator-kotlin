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

import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class FieldNamerTest {

    val typeMap: ProtobufTypeMapper = mock()

    @BeforeTest
    fun before() {
        reset(typeMap)
    }

    @Test
    fun `can generate setter code`() {
        val fieldInfo = getFieldInfo(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("the_thing")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .build(),
            String::class.asTypeName()
        )

        val setter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), false)
        assertThat(setter.toString()).isEqualTo(".setTheThing(5)")

        val dslSetter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), true)
        assertThat(dslSetter.toString()).isEqualTo("theThing = 5")

        assertThat(FieldNamer.getAccessorName(typeMap, fieldInfo)).isEqualTo("theThing")
    }

    @Test
    fun `can generate setter code for repeated fields`() {
        val fieldInfo = getFieldInfo(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("thing")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                .build(),
            String::class.asTypeName()
        )

        val setter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), false)
        assertThat(setter.toString()).isEqualTo(".addAllThing(5)")

        val dslSetter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), true)
        assertThat(dslSetter.toString()).isEqualTo("addAllThing(5)")

        assertThat(FieldNamer.getAccessorName(typeMap, fieldInfo)).isEqualTo("thingList")
    }

    @Test
    fun `can generate setter code for repeated fields at index`() {
        val fieldInfo = getFieldInfo(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("thing")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                .build(),
            String::class.asTypeName(),
            2
        )

        val setter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), false)
        assertThat(setter.toString()).isEqualTo(".addThing(2, 5)")

        val dslSetter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), true)
        assertThat(dslSetter.toString()).isEqualTo("addThing(2, 5)")

        assertThat(FieldNamer.getAccessorName(typeMap, fieldInfo)).isEqualTo("thing[2]")
    }

    @Test
    fun `can generate setter code for map fields`() {
        whenever(typeMap.hasProtoEnumDescriptor(any())).doReturn(false)
        whenever(typeMap.getProtoTypeDescriptor(any())).thenReturn(
            DescriptorProtos.DescriptorProto.newBuilder().setOptions(
                DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true).build()
            ).build()
        )

        val fieldInfo = getFieldInfo(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("thing")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName("mymap")
                .build(),
            String::class.asTypeName(),
            2
        )

        val setter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), false)
        assertThat(setter.toString()).isEqualTo(".putAllThing(5)")

        val dslSetter = FieldNamer.getSetterCode(typeMap, fieldInfo, CodeBlock.of("5"), true)
        assertThat(dslSetter.toString()).isEqualTo("putAllThing(5)")

        assertThat(FieldNamer.getAccessorName(typeMap, fieldInfo)).isEqualTo("thingMap")
    }

    private fun getFieldInfo(field: DescriptorProtos.FieldDescriptorProto, type: TypeName, index: Int = -1): ProtoFieldInfo {
        val protoMessage = DescriptorProtos.DescriptorProto.newBuilder()
            .addField(field)
            .build()
        val protoFile = DescriptorProtos.FileDescriptorProto.newBuilder()
            .addMessageType(protoMessage)
            .build()
        return ProtoFieldInfo(protoFile, protoMessage, field, index, type)
    }

    @Test
    fun `can get setter name`() =
        testSetterMethod(FieldNamer::getSetterName, "set")

    @Test
    fun `can get setter map name`() =
        testSetterMethod(FieldNamer::getSetterMapName, "putAll")

    @Test
    fun `can get setter repeated name`() =
        testSetterMethod(FieldNamer::getSetterRepeatedName, "addAll")

    @Test
    fun `can get setter repeated at index name`() =
        testSetterMethod(FieldNamer::getSetterRepeatedAtIndexName, "add")

    private fun testSetterMethod(method: (protoFieldName: String) -> String, prefix: String = "") {
        assertThat(method("z")).isEqualTo("${prefix}Z")
        assertThat(method("zero")).isEqualTo("${prefix}Zero")
        assertThat(method("ok_go")).isEqualTo("${prefix}OkGo")
        assertThat(method("hello")).isEqualTo("${prefix}Hello")
    }

    @Test
    fun `can get accessor name`() =
        testAccessorMethod(FieldNamer::getAccessorName)

    @Test
    fun `can get repeated accessor name`() =
        testAccessorMethod(FieldNamer::getAccessorRepeatedName, "List")

    @Test
    fun `can get repeated at index accessor name`() =
        testAccessorMethod(FieldNamer::getAccessorRepeatedAtIndexName)

    @Test
    fun `can get parameter name`() =
        testAccessorMethod(FieldNamer::getParameterName)

    private fun testAccessorMethod(
        method: (protoFieldName: String, value: CodeBlock?) -> String,
        suffix: String = ""
    ) {
        assertThat(method("boo_hoo", null))
            .isEqualTo("booHoo$suffix")
        assertThat(method("a", null))
            .isEqualTo("a$suffix")
        assertThat(method("one_TWO", null))
            .isEqualTo("oneTwo$suffix")
        assertThat(method("three_two_one", null))
            .isEqualTo("threeTwoOne$suffix")

        assertThat(method("a", CodeBlock.of("a")))
            .isEqualTo("this.a$suffix")
        assertThat(method("a", CodeBlock.of("b")))
            .isEqualTo("a$suffix")
        assertThat(method("ahoy_there", CodeBlock.of("ahoyThere")))
            .isEqualTo("this.ahoyThere$suffix")
        assertThat(method("ahoy_there", CodeBlock.of("ahoy_there")))
            .isEqualTo("ahoyThere$suffix")
        assertThat(method("ahoy_there", CodeBlock.of("ahoy")))
            .isEqualTo("ahoyThere$suffix")
    }
}