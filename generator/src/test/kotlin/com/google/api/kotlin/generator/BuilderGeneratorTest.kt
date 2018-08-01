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
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

class BuilderGeneratorTest {

    @Test
    fun `generates builders`() {
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllKotlinTypes() }.thenReturn(listOf(
                    "com.google.api.Foo",
                    "com.google.api.Bar"
            ))
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(2)

        fun methodBody(type: String) = "return com.google.api.$type.newBuilder().apply(init).build()"
        listOf("Foo", "Bar").forEach { name ->
            val f = funs.find { it.name == name } ?: throw Exception("fun not found $name")
            assertThat(f.body.asNormalizedString()).isEqualTo(methodBody(name))
            assertThat(f.parameters).hasSize(1)
            assertThat(f.parameters.first().name).isEqualTo("init")
        }
    }

    @Test
    fun `generates nested builders`() {
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllKotlinTypes() }.thenReturn(listOf(
                "com.google.api.Foo",
                "com.google.api.Foo.A",
                "com.google.api.Foo.A.B",
                "com.google.api.Foo.A.B.C",
                "com.google.api.Bar",
                "com.google.api.Bar.X",
                "com.google.api.Bar.Y",
                "com.google.api.Bar.Z",
                "com.google.api.Baz"
            ))
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.functions
        assertThat(funs).hasSize(9)

        fun methodBody(type: String) = "return com.google.api.$type.newBuilder().apply(init).build()"
        listOf("Foo", "Foo.A", "Foo.A.B", "Foo.A.B.C", "Bar", "Bar.X", "Bar.Y", "Bar.Z", "Baz").forEach { qualifiedName ->
            val path = qualifiedName.split(".")
            val f = funs.find { it.name == path.last() } ?: throw Exception("fun not found $qualifiedName")
            assertThat(f.body.asNormalizedString()).isEqualTo(methodBody(qualifiedName))
            assertThat(f.parameters).hasSize(1)
            assertThat(f.parameters.first().name).isEqualTo("init")
            if (path.size > 1) {
                assertThat(f.receiverType).isEqualTo(ClassName("com.google.api",
                    path.subList(0, path.size - 1).joinToString(".")))
            }
        }
    }

    @Test
    fun `skips descriptor types`() {
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllKotlinTypes() }.thenReturn(listOf(
                    "com.google.protobuf.DescriptorProtos",
                    "com.google.protobuf.FileDescriptorProtos"
            ))
        }

        // build types
        val files = BuilderGenerator().generate(typeMap)

        assertThat(files).isEmpty()
    }

    @Test
    fun `skips Any and Empty`() {
        val typeMap: ProtobufTypeMapper = mock {
            on { getAllKotlinTypes() }.thenReturn(listOf(
                    "com.google.protobuf.Any",
                    "com.google.protobuf.Empty",
                    "com.google.protobuf.Surprise"
            ))
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
