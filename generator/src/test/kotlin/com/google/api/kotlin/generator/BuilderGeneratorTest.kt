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

package com.google.api.kotlin.generator

import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.TypeNamePair
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

internal class BuilderGeneratorTest {

    @Test
    fun `generates builders`() {
        val types = listOf(
            "com.google.api.Foo",
            "com.google.api.Bar"
        )
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllTypes() }.thenReturn(types.map { TypeNamePair(".$it", it) })
            on { getProtoTypeDescriptor(any()) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder().build()
            )
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(2)

        fun methodBody(type: String) =
            """
            |fun $type(
            |    init: (@com.google.api.kgax.ProtoBuilder com.google.api.$type.Builder).() -> kotlin.Unit
            |): com.google.api.$type =
            |    com.google.api.$type.newBuilder().apply(init).build()
            |""".trimMargin().asNormalizedString()

        listOf("Foo", "Bar").forEach { name ->
            val f = funs.find { it.name == name } ?: throw Exception("fun not found $name")
            assertThat(f.toString().asNormalizedString()).isEqualTo(methodBody(name))
        }
    }

    @Test
    fun `generates nested builders`() {
        val types = listOf(
            "com.google.api.Foo",
            "com.google.api.Foo.A",
            "com.google.api.Foo.A.B",
            "com.google.api.Foo.A.B.C",
            "com.google.api.Bar",
            "com.google.api.Bar.X",
            "com.google.api.Bar.Y",
            "com.google.api.Bar.Z",
            "com.google.api.Baz"
        )
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllTypes() }.thenReturn(types.map { TypeNamePair(".$it", it) })
            on { getProtoTypeDescriptor(any()) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder().build()
            )
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(9)

        fun methodBody(type: String) =
            """
            |fun ${type.replace(".", "_")}(
            |    init: (@com.google.api.kgax.ProtoBuilder com.google.api.$type.Builder).() -> kotlin.Unit
            |): com.google.api.$type =
            |    com.google.api.$type.newBuilder().apply(init).build()
            """.asNormalizedString()
        listOf(
            "Foo.A",
            "Foo.A.B",
            "Foo.A.B.C",
            "Bar.X",
            "Bar.Y",
            "Bar.Z"
        ).forEach { qualifiedName ->
            val path = qualifiedName.split(".")
            val f = funs.first { it.name == path.joinToString("_") }
            assertThat(f.toString().asNormalizedString()).isEqualTo(methodBody(qualifiedName))
        }
    }

    @Test
    fun `generates repeated setters`() {
        val types = listOf(
            "com.google.api.Foo"
        )
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllTypes() }.thenReturn(types.map { TypeNamePair(".$it", it) })
            on { getProtoTypeDescriptor(eq(".com.google.api.Foo")) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("responses")
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".com.google.api.Response")
                            .build()
                    )
                    .build()
            )
            on { getProtoTypeDescriptor(eq(".com.google.api.Response")) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder().build()
            )
            on { getKotlinType(eq(".com.google.api.Response")) }.thenReturn(ClassName("com.google.api", "Response"))
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(1)

        val props = file.properties
        assertThat(props).hasSize(1)

        val repeatedSetter = props.first()
        assertThat(repeatedSetter.toString().asNormalizedString()).isEqualTo(
            """
            |var com.google.api.Foo.Builder.responses: kotlin.collections.List<com.google.api.Response>
            |    get() = this.responsesList
            |    set(values) { this.addAllResponses(values) }
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates repeated setters with primities`() {
        val types = listOf(
            "com.google.api.Foo"
        )
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllTypes() }.thenReturn(types.map { TypeNamePair(".$it", it) })
            on { getProtoTypeDescriptor(eq(".com.google.api.Foo")) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("the_strings")
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                            .build()
                    )
                    .build()
            )
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(1)

        val props = file.properties
        assertThat(props).hasSize(1)

        val repeatedSetter = props.first()
        assertThat(repeatedSetter.toString().asNormalizedString()).isEqualTo(
            """
            |var com.google.api.Foo.Builder.theStrings: kotlin.collections.List<kotlin.String>
            |    get() = this.theStringsList
            |    set(values) { this.addAllTheStrings(values) }
            """.asNormalizedString()
        )
    }

    @Test
    fun `skips descriptor types`() {
        val types = listOf(
            "com.google.protobuf.DescriptorProtos",
            "com.google.protobuf.FileDescriptorProtos"
        )
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllTypes() }.thenReturn(types.map { TypeNamePair(".$it", it) })
            on { getProtoTypeDescriptor(any()) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder().build()
            )
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).isEmpty()
    }

    @Test
    fun `skips Any and Empty`() {
        val types = listOf(
            "com.google.protobuf.Any",
            "com.google.protobuf.Empty",
            "com.google.protobuf.Surprise"
        )
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllTypes() }.thenReturn(types.map { TypeNamePair(".$it", it) })
            on { getProtoTypeDescriptor(any()) }.thenReturn(
                DescriptorProtos.DescriptorProto.newBuilder().build()
            )
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.protobuf")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(1)
        assertThat(funs.first().name).isEqualTo("Surprise")
    }
}
