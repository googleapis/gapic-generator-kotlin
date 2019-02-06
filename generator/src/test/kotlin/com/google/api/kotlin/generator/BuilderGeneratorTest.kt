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
import com.google.api.kotlin.GeneratedSource
import com.google.api.kotlin.MockedProtoUtil
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
        val mocked = MockedProtoUtil.getBasicMockedProto()

        // build types
        val files = DSLBuilderGenerator().generate(mocked.typeMap)

        assertThat(files.map { it.packageName }).containsExactly("test", "test.the")
        assertThat(files.map { it.name }).containsExactlyElementsIn(Array(2) { "KotlinBuilders" })

        assertThat(files.flatMap { f -> f.types.map { it.toString().asNormalizedString() } }).containsExactly(
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class InputDsl(val builder: test.the.Input.Builder) {
            |    inline var str: kotlin.String
            |        get() = builder.str
            |        set(value) { builder.str = value }
            |
            |    inline var foo: test.Bar
            |        get() = builder.foo
            |        set(value) { builder.foo = value }
            |}
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class BarDsl(val builder: test.Bar.Builder) {
            |    inline var bar: kotlin.Boolean
            |        get() = builder.bar
            |        set(value) { builder.bar = value }
            |}
            """.asNormalizedString()
        )

        assertThat(files.flatMap { f -> f.functions.map { it.toString().asNormalizedString() } }).containsExactly(
            """
            |fun input(init: test.the.InputDsl.() -> kotlin.Unit): test.the.Input {
            |    val builder = test.the.Input.newBuilder()
            |    test.the.InputDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun bar(init: test.BarDsl.() -> kotlin.Unit): test.Bar {
            |    val builder = test.Bar.newBuilder()
            |    test.BarDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString()
        )
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

        // verify a few of the types
        assertThat(file.types.map { it.toString().asNormalizedString() }).containsExactly(
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class FooDsl(val builder: com.google.api.Foo.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class Foo_ADsl(val builder: com.google.api.Foo.A.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class Foo_A_BDsl(val builder: com.google.api.Foo.A.B.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class Foo_A_B_CDsl(val builder: com.google.api.Foo.A.B.C.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class BarDsl(val builder: com.google.api.Bar.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class Bar_XDsl(val builder: com.google.api.Bar.X.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class Bar_YDsl(val builder: com.google.api.Bar.Y.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class Bar_ZDsl(val builder: com.google.api.Bar.Z.Builder)
            """.asNormalizedString(),
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class BazDsl(val builder: com.google.api.Baz.Builder)
            """.asNormalizedString()
        )

        // verify a few of the functions
        assertThat(file.functions.map { it.toString().asNormalizedString() }).containsExactly(
            """
            |fun foo(init: com.google.api.FooDsl.() -> kotlin.Unit): com.google.api.Foo {
            |    val builder = com.google.api.Foo.newBuilder()
            |    com.google.api.FooDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun foo_A(init: com.google.api.Foo_ADsl.() -> kotlin.Unit): com.google.api.Foo.A {
            |    val builder = com.google.api.Foo.A.newBuilder()
            |    com.google.api.Foo_ADsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun foo_A_B(init: com.google.api.Foo_A_BDsl.() -> kotlin.Unit): com.google.api.Foo.A.B {
            |    val builder = com.google.api.Foo.A.B.newBuilder()
            |    com.google.api.Foo_A_BDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun foo_A_B_C(init: com.google.api.Foo_A_B_CDsl.() -> kotlin.Unit): com.google.api.Foo.A.B.C {
            |    val builder = com.google.api.Foo.A.B.C.newBuilder()
            |    com.google.api.Foo_A_B_CDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun bar(init: com.google.api.BarDsl.() -> kotlin.Unit): com.google.api.Bar {
            |    val builder = com.google.api.Bar.newBuilder()
            |    com.google.api.BarDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun bar_X(init: com.google.api.Bar_XDsl.() -> kotlin.Unit): com.google.api.Bar.X {
            |    val builder = com.google.api.Bar.X.newBuilder()
            |    com.google.api.Bar_XDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun bar_Y(init: com.google.api.Bar_YDsl.() -> kotlin.Unit): com.google.api.Bar.Y {
            |    val builder = com.google.api.Bar.Y.newBuilder()
            |    com.google.api.Bar_YDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun bar_Z(init: com.google.api.Bar_ZDsl.() -> kotlin.Unit): com.google.api.Bar.Z {
            |    val builder = com.google.api.Bar.Z.newBuilder()
            |    com.google.api.Bar_ZDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString(),
            """
            |fun baz(init: com.google.api.BazDsl.() -> kotlin.Unit): com.google.api.Baz {
            |    val builder = com.google.api.Baz.newBuilder()
            |    com.google.api.BazDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString()
        )
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

        assertThat(file.types).hasSize(1)
        assertThat(file.types.first().toString().asNormalizedString()).isEqualTo(
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class FooDsl(val builder: com.google.api.Foo.Builder) {
            |    inline var responses: kotlin.collections.List<com.google.api.Response>
            |        get() = builder.responsesList
            |        set(values) { builder.clearResponses() builder.addAllResponses(values) }
            |
            |    inline fun responses(vararg values: com.google.api.Response) { builder.addAllResponses(values.toList()) }
            |}
            """.asNormalizedString()
        )
        assertThat(file.functions).hasSize(1)
        assertThat(file.functions.first().toString().asNormalizedString()).isEqualTo(
            """
            |fun foo(init: com.google.api.FooDsl.() -> kotlin.Unit): com.google.api.Foo {
            |    val builder = com.google.api.Foo.newBuilder()
            |    com.google.api.FooDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString()
        )
        assertThat(file.properties).isEmpty()
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
        assertThat(file.types).hasSize(1)
        assertThat(file.types.first().toString().asNormalizedString()).isEqualTo(
            """
            |@com.google.api.kgax.ProtoBuilder
            |inline class FooDsl(val builder: com.google.api.Foo.Builder) {
            |    inline var theStrings: kotlin.collections.List<kotlin.String>
            |        get() = builder.theStringsList
            |        set(values) { builder.clearTheStrings() builder.addAllTheStrings(values) }
            |
            |    inline fun theStrings(vararg values: kotlin.String) { builder.addAllTheStrings(values.toList()) }
            |}
            """.asNormalizedString()
        )
        assertThat(file.functions).hasSize(1)
        assertThat(file.functions.first().toString().asNormalizedString()).isEqualTo(
            """
            |fun foo(init: com.google.api.FooDsl.() -> kotlin.Unit): com.google.api.Foo {
            |    val builder = com.google.api.Foo.newBuilder()
            |    com.google.api.FooDsl(builder).apply(init)
            |    return builder.build()
            |}
            """.asNormalizedString()
        )
        assertThat(file.properties).isEmpty()
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
        assertThat(funs.first().name).isEqualTo("surprise")
    }

    @Test
    fun `generates repeated getters and setters`() {
        val builders = generate().kotlinBuilders()

        assertThat(builders.builderTypeProp("Detail", "lotsMore")).isEqualTo(
            """
            |inline var lotsMore: kotlin.collections.List<google.example.MoreDetail>
            |    get() = builder.lotsMoreList
            |    set(values) { builder.clearLotsMore() builder.addAllLotsMore(values) }
            """.asNormalizedString()
        )
        assertThat(builders.builderTypeFun("Detail", "lotsMore")).isEqualTo(
            """
            |inline fun lotsMore(vararg values: google.example.MoreDetail) { builder.addAllLotsMore(values.toList()) }
            """.asNormalizedString()
        )

        assertThat(builders.builderTypeProp("TestRequest", "moreDetails")).isEqualTo(
            """
            |inline var moreDetails: kotlin.collections.List<google.example.Detail>
            |    get() = builder.moreDetailsList
            |    set(values) { builder.clearMoreDetails() builder.addAllMoreDetails(values) }
            """.asNormalizedString()
        )
        assertThat(builders.builderTypeFun("TestRequest", "moreDetails")).isEqualTo(
            """
            |inline fun moreDetails(vararg values: google.example.Detail) { builder.addAllMoreDetails(values.toList()) }
            """.asNormalizedString()
        )

        assertThat(builders.builderTypeProp("PagedResponse", "responses")).isEqualTo(
            """
            |inline var responses: kotlin.collections.List<kotlin.Int>
            |    get() = builder.responsesList
            |    set(values) { builder.clearResponses() builder.addAllResponses(values) }
            """.asNormalizedString()
        )

        assertThat(builders.builderTypeFun("PagedResponse", "responses")).isEqualTo(
            """
            |inline fun responses(vararg values: kotlin.Int) { builder.addAllResponses(values.toList()) }
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates map getters and setters`() {
        val builders = generate().kotlinBuilders()

        assertThat(builders.builderTypeProp("Detail", "tonsMore")).isEqualTo(
            """
            |inline var tonsMore: kotlin.collections.Map<kotlin.String, google.example.MoreDetail>
            |    get() = builder.tonsMoreMap
            |    set(values) { builder.clearTonsMore() builder.putAllTonsMore(values) }
            """.asNormalizedString()
        )
        assertThat(builders.builderTypeFun("Detail", "tonsMore")).isEqualTo(
            """
            |inline fun tonsMore(vararg values: kotlin.Pair<kotlin.String, google.example.MoreDetail>) { builder.putAllTonsMore(values.toMap()) }
            """.asNormalizedString()
        )
    }

    @Test
    fun `generates the correct number of builder functions`() {
        val builders = generate().kotlinBuilders()

        assertThat(builders.types).hasSize(16)
        assertThat(builders.functions).hasSize(16)
        assertThat(builders.properties).isEmpty()
    }
}

private fun GeneratedSource.builderType(name: String) = this.types.first { it.name == "${name}Dsl" }
private fun GeneratedSource.builderTypeFun(name: String, method: String) =
    this.builderType(name).funSpecs.first { it.name == method }.toString().asNormalizedString()

private fun GeneratedSource.builderTypeProp(name: String, property: String) =
    this.builderType(name).propertySpecs.first { it.name == property }.toString().asNormalizedString()
