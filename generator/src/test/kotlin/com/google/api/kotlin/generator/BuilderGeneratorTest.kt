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

import com.google.api.kotlin.BaseBuilderGeneratorTest
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.TypeNamePair
import com.google.api.kotlin.kotlinBuilders
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

internal class BuilderGeneratorTest : BaseBuilderGeneratorTest(DSLBuilderGenerator()) {

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
        val files = DSLBuilderGenerator().generate(typeMap)

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
        val files = DSLBuilderGenerator().generate(typeMap)

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
    fun `generates repeated helpers`() {
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
        val files = DSLBuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val builderFuns = file.functions.filterNot { it.name == "responses" }
        assertThat(builderFuns).hasSize(1)

        val repeatedFuns = file.functions.filter { it.name == "responses" }
        assertThat(repeatedFuns).hasSize(1)

        val repeatedSetter = repeatedFuns.first()
        assertThat(repeatedSetter.toString().asNormalizedString()).isEqualTo(
            """
            |fun com.google.api.Foo.Builder.responses(vararg values: com.google.api.Response) {
            |    this.addAllResponses(values.toList())
            |}
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates repeated setters with primitives`() {
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
        val files = DSLBuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val builderFuns = file.functions.filterNot { it.name == "theStrings" }
        assertThat(builderFuns).hasSize(1)

        val repeatedFuns = file.functions.filter { it.name == "theStrings" }
        assertThat(repeatedFuns).hasSize(1)

        val repeatedSetter = repeatedFuns.first()
        assertThat(repeatedSetter.toString().asNormalizedString()).isEqualTo(
            """
            |fun com.google.api.Foo.Builder.theStrings(vararg values: kotlin.String) {
            |    this.addAllTheStrings(values.toList())
            |}
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
        val files = DSLBuilderGenerator().generate(typeMap)

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
        val files = DSLBuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.protobuf")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(1)
        assertThat(funs.first().name).isEqualTo("Surprise")
    }

    @Test
    fun `generates repeated getters and setters`() {
        val builders = generate().kotlinBuilders()
        var props = builders.functions.filter { it.name == "lotsMore" }
        assertThat(props).hasSize(1)

        var prop = props.first()
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |fun google.example.Detail.Builder.lotsMore(vararg values: google.example.MoreDetail) {
            |    this.addAllLotsMore(values.toList())
            |}
            """.asNormalizedString()
        )

        props = builders.functions.filter { it.name == "moreDetails" }
        assertThat(props).hasSize(1)

        prop = props.first()
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |fun google.example.TestRequest.Builder.moreDetails(vararg values: google.example.Detail) {
            |    this.addAllMoreDetails(values.toList())
            |}
            """.asNormalizedString()
        )

        props = builders.functions.filter { it.name == "responses" }
        assertThat(props).hasSize(2)
        assertThat(props.map { it.toString().asNormalizedString() }).containsExactly(
            """
            |fun google.example.PagedResponse.Builder.responses(vararg values: kotlin.Int) {
            |    this.addAllResponses(values.toList())
            |}
            """.asNormalizedString(),
            """
            |fun google.example.StillNotPagedResponse.Builder.responses(vararg values: kotlin.String) {
            |    this.addAllResponses(values.toList())
            |}
            """.asNormalizedString())
    }

    @Test
    fun `generates map getters and setters`() {
        val builders = generate().kotlinBuilders()
        val props = builders.functions.filter { it.name == "tonsMore" }
        assertThat(props).hasSize(1)

        val prop = props.first()
        assertThat(prop.toString().asNormalizedString()).isEqualTo(
            """
            |fun google.example.Detail.Builder.tonsMore(
            |    vararg values: kotlin.Pair<kotlin.String, google.example.MoreDetail>
            |) {
            |    this.putAllTonsMore(values.toMap())
            |}
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates builder functions`() {
        val builders = generate().kotlinBuilders()
        val funs = builders.functions

        assertThat(funs).hasSize(21)

        // verify one of them
        val b = funs.filter { it.name == "Result" }
        assertThat(b).hasSize(1)
        assertThat(b.first().toString().asNormalizedString()).isEqualTo(
            """
            |fun Result(
            |    init: (@com.google.api.kgax.ProtoBuilder google.example.Result.Builder).() -> kotlin.Unit
            |): google.example.Result =
            |    google.example.Result.newBuilder().apply(init).build()
            """.asNormalizedString()
        )
    }
}
