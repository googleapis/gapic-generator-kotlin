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

import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.CodeBlock
import kotlin.test.Test

internal class FieldNamerTest {

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