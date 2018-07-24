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
import com.google.api.kotlin.generator.config.ProtobufTypeMapper
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.FunSpec
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
        val files = BuilderGenerator().generate(typeMap).map { it.build() }

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.api")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.members.mapNotNull { it as? FunSpec }
        assertThat(funs).hasSize(2)

        fun methodBody(type: String) = "return com.google.api.$type.newBuilder().apply(init).build()"
        val foo = funs.find { it.name == "Foo" }!!
        assertThat(foo.body.asNormalizedString()).isEqualTo(methodBody("Foo"))
        val bar = funs.find { it.name == "Bar" }!!
        assertThat(bar.body.asNormalizedString()).isEqualTo(methodBody("Bar"))

        for (f in listOf(foo, bar)) {
            assertThat(f.parameters).hasSize(1)
            assertThat(f.parameters.first().name).isEqualTo("init")
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
        val files = BuilderGenerator().generate(typeMap).map { it.build() }

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
        val files = BuilderGenerator().generate(typeMap).map { it.build() }

        assertThat(files).hasSize(1)
        val file = files.first()
        assertThat(file.packageName).isEqualTo("com.google.protobuf")
        assertThat(file.name).isEqualTo("KotlinBuilders")

        val funs = file.members.mapNotNull { it as? FunSpec }
        assertThat(funs).hasSize(1)
        assertThat(funs.first().name).isEqualTo("Surprise")
    }
}
