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

package com.google.api.kotlin.config

import com.google.api.kotlin.BaseGeneratorTest
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal class ProtobufTypeMapperTest : BaseGeneratorTest(GRPCGenerator()) {

    @Test
    fun `maps all Kotlin types`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertThat(mapper.getAllKotlinTypes()).containsExactly(
            "google.example.TestRequest",
            "google.example.TestResponse",
            "google.example.Result",
            "google.example.Detail",
            "google.example.MoreDetail",
            "google.example.PagedRequest",
            "google.example.NotPagedRequest",
            "google.example.PagedResponse",
            "google.example.NotPagedResponse",
            "google.example.StillNotPagedResponse",
            "google.example.SomeResponse",
            "google.example.SomeMetadata"
        )
    }

    @Test
    fun `maps all enums`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertThat(mapper.hasProtoEnumDescriptor(".google.example.MoreDetail.HowMuchMore")).isTrue()
        assertThat(mapper.getProtoEnumDescriptor(".google.example.MoreDetail.HowMuchMore").name)
            .isEqualTo("HowMuchMore")
        assertThat(mapper.hasProtoEnumDescriptor(".google.example.AnEnum")).isTrue()
        assertThat(mapper.getProtoEnumDescriptor(".google.example.AnEnum").name)
            .isEqualTo("AnEnum")
    }

    @Test
    fun `maps type descriptors`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertThat(mapper.hasProtoTypeDescriptor(".google.example.Result")).isTrue()
        assertThat(mapper.getProtoTypeDescriptor(".google.example.Result").name)
            .isEqualTo("Result")
    }

    @Test
    fun `throws on non enum`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertThat(mapper.hasProtoEnumDescriptor(".google.example.Result")).isFalse()
        assertFailsWith(IllegalArgumentException::class) {
            mapper.getProtoEnumDescriptor(".google.example.Result")
        }
    }

    @Test
    fun `throws on non type`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertThat(mapper.hasProtoTypeDescriptor(".google.example.AnEnum")).isFalse()
        assertFailsWith(IllegalArgumentException::class) {
            mapper.getProtoTypeDescriptor(".google.example.AnEnum")
        }
    }

    @Test
    fun `gets Kotlin types`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertThat(mapper.getKotlinType(".google.example.MoreDetail"))
            .isEqualTo(ClassName("google.example", "MoreDetail"))
        assertThat(mapper.getKotlinType(".google.example.AnEnum"))
            .isEqualTo(ClassName("google.example", "AnEnum"))
        assertThat(mapper.getKotlinType(".google.example.MoreDetail.HowMuchMore"))
            .isEqualTo(ClassName("google.example", "MoreDetail.HowMuchMore"))
    }

    @Test
    fun `throws on invalid Kotlin types`() {
        val mapper = ProtobufTypeMapper.fromProtos(listOf(testProto, testTypesProto))

        assertFailsWith(IllegalArgumentException::class) {
            mapper.getKotlinType(".google.foo.Bar")
        }
    }

    @Test
    fun `maps a test type`() {
        val message = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("TheThing")
            .build()
        val proto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setPackage("my.package")
            .setName("great_proto.proto")
            .setOptions(DescriptorProtos.FileOptions.newBuilder()
                .setJavaMultipleFiles(true)
                .build())
            .addMessageType(message)
            .build()

        val mapper = ProtobufTypeMapper.fromProtos(listOf(proto))
        assertThat(mapper.getAllKotlinTypes()).containsExactly("my.package.TheThing")
        assertThat(mapper.getProtoTypeDescriptor(".my.package.TheThing")).isEqualTo(message)
        assertThat(mapper.getKotlinType(".my.package.TheThing"))
            .isEqualTo(ClassName("my.package", "TheThing"))
    }

    @Test
    fun `maps a test type with an outer class name`() {
        val message = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("TheThing")
            .build()
        val proto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setPackage("my.package")
            .setName("great_proto.proto")
            .setOptions(DescriptorProtos.FileOptions.newBuilder()
                .setJavaMultipleFiles(false)
                .setJavaOuterClassname("OutAndAbout")
                .build())
            .addMessageType(message)
            .build()

        val mapper = ProtobufTypeMapper.fromProtos(listOf(proto))
        assertThat(mapper.getAllKotlinTypes()).containsExactly("my.package.OutAndAbout.TheThing")
        assertThat(mapper.getProtoTypeDescriptor(".my.package.TheThing")).isEqualTo(message)
        assertThat(mapper.getKotlinType(".my.package.TheThing"))
            .isEqualTo(ClassName("my.package", "OutAndAbout.TheThing"))
    }

    @Test
    fun `maps a test type with an outer class nested name`() {
        val message = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("TheThing")
            .build()
        val proto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setPackage("my.package")
            .setName("/one/two/great_proto.proto")
            .setOptions(DescriptorProtos.FileOptions.newBuilder()
                .setJavaMultipleFiles(false)
                .setJavaOuterClassname("OutAndAbout")
                .build())
            .addMessageType(message)
            .build()

        val mapper = ProtobufTypeMapper.fromProtos(listOf(proto))
        assertThat(mapper.getAllKotlinTypes()).containsExactly("my.package.OutAndAbout.TheThing")
        assertThat(mapper.getProtoTypeDescriptor(".my.package.TheThing")).isEqualTo(message)
        assertThat(mapper.getKotlinType(".my.package.TheThing"))
            .isEqualTo(ClassName("my.package", "OutAndAbout.TheThing"))
    }

    @Test
    fun `maps a test type with an outer class nested name collision`() {
        val message = DescriptorProtos.DescriptorProto.newBuilder()
            .setName("GreatProto")
            .build()
        val proto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setPackage("my.package")
            .setName("/one/two/great_proto.proto")
            .setOptions(DescriptorProtos.FileOptions.newBuilder()
                .setJavaMultipleFiles(false)
                .build())
            .addMessageType(message)
            .build()

        val mapper = ProtobufTypeMapper.fromProtos(listOf(proto))
        assertThat(mapper.getAllKotlinTypes()).containsExactly("my.package.GreatProtoOuterClass.GreatProto")
        assertThat(mapper.getProtoTypeDescriptor(".my.package.GreatProto")).isEqualTo(message)
        assertThat(mapper.getKotlinType(".my.package.GreatProto"))
            .isEqualTo(ClassName("my.package", "GreatProtoOuterClass.GreatProto"))
    }
}
