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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
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

        assertThat(FieldNamer.getDslSetterCode(typeMap, fieldInfo, CodeBlock.of("\"5\"")).toString())
            .isEqualTo("this.theThing = \"5\"")

        assertThat(FieldNamer.getJavaAccessorName(typeMap, fieldInfo)).isEqualTo("theThing")
    }

    @Test
    fun `can generate setter code with qualifier`() {
        val fieldInfo = getFieldInfo(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("some_other_thing")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .build(),
            String::class.asTypeName()
        )

        assertThat(FieldNamer.getDslSetterCode(typeMap, fieldInfo, CodeBlock.of("someOtherThing")).toString())
            .isEqualTo("this.someOtherThing = someOtherThing")

        assertThat(FieldNamer.getJavaAccessorName(typeMap, fieldInfo)).isEqualTo("someOtherThing")
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

        assertThat(FieldNamer.getDslSetterCode(typeMap, fieldInfo, CodeBlock.of("5")).toString())
            .isEqualTo("this.thing = 5")

        assertThat(FieldNamer.getDslSetterCode(typeMap, fieldInfo, CodeBlock.of("listOf(5, 6, 7)")).toString())
            .isEqualTo("this.thing = listOf(5, 6, 7)")

        assertThat(FieldNamer.getJavaAccessorName(typeMap, fieldInfo)).isEqualTo("thingList")
    }

    @Test
    fun `can generate accessor code for repeated fields at index`() {
        val fieldInfo = getFieldInfo(
            DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("thing")
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                .build(),
            String::class.asTypeName(),
            2
        )

        assertThat(FieldNamer.getJavaAccessorName(typeMap, fieldInfo)).isEqualTo("thingList[2]")
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

        assertThat(FieldNamer.getDslSetterCode(typeMap, fieldInfo, CodeBlock.of("stuff")).toString())
            .isEqualTo("this.thing = stuff")

        assertThat(FieldNamer.getJavaAccessorName(typeMap, fieldInfo)).isEqualTo("thingMap")
    }

    private fun getFieldInfo(
        field: DescriptorProtos.FieldDescriptorProto,
        type: ClassName,
        index: Int = -1
    ): ProtoFieldInfo {
        val protoMessage = DescriptorProtos.DescriptorProto.newBuilder()
            .addField(field)
            .build()
        val protoFile = DescriptorProtos.FileDescriptorProto.newBuilder()
            .addMessageType(protoMessage)
            .build()
        return ProtoFieldInfo(protoFile, protoMessage, field, index, type)
    }

    @Test
    fun `can generate field names`() {
        assertThat(FieldNamer.getFieldName("fun_stuff")).isEqualTo("funStuff")
        assertThat(FieldNamer.getFieldName("a")).isEqualTo("a")
        assertThat(FieldNamer.getFieldName("xyz")).isEqualTo("xyz")
        assertThat(FieldNamer.getFieldName("a_long_name_yes")).isEqualTo("aLongNameYes")
        assertThat(FieldNamer.getFieldName("justaword")).isEqualTo("justaword")
    }

    @Test
    fun `can generate Java builder setter names`() {
        assertThat(FieldNamer.getJavaBuilderRawSetterName("a_field")).isEqualTo("setAField")
        assertThat(FieldNamer.getJavaBuilderSyntheticSetterName("a_field")).isEqualTo("aField")
        assertThat(FieldNamer.getJavaBuilderSetterMapName("a_field")).isEqualTo("putAllAField")
        assertThat(FieldNamer.getJavaBuilderSetterRepeatedName("a_field")).isEqualTo("addAllAField")
    }

    @Test
    fun `can generate Java builder accessor names`() {
        assertThat(FieldNamer.getJavaBuilderAccessorName("a_field")).isEqualTo("aField")
        assertThat(FieldNamer.getJavaBuilderAccessorMapName("a_field")).isEqualTo("aFieldMap")
        assertThat(FieldNamer.getJavaBuilderAccessorRepeatedName("a_field")).isEqualTo("aFieldList")
    }

    @Test
    fun `can recognize reserved names`() {
        assertThat(FieldNamer.getJavaBuilderRawSetterName("if")).isEqualTo("setIf")
        assertThat(FieldNamer.getJavaBuilderSyntheticSetterName("if")).isEqualTo("`if`")
        assertThat(FieldNamer.getJavaBuilderSetterMapName("if")).isEqualTo("putAllIf")
        assertThat(FieldNamer.getJavaBuilderSetterRepeatedName("if")).isEqualTo("addAllIf")
        assertThat(FieldNamer.getJavaBuilderAccessorName("if")).isEqualTo("`if`")
        assertThat(FieldNamer.getJavaBuilderAccessorMapName("if")).isEqualTo("ifMap")
        assertThat(FieldNamer.getJavaBuilderAccessorRepeatedName("if")).isEqualTo("ifList")
    }
}
