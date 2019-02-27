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

package com.google.api.kotlin.util

import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.CodeBlock
import kotlin.test.Test

internal class FormatterTest {
    @Test
    fun `can format code`() {
        val code = CodeBlock.of("fun foo(x:     Int)       =    2").format()
        assertThat(code).isEqualTo("fun foo(x: Int) = 2")
    }

    @Test
    fun `can format non-top-level sample code`() {
        val code = CodeBlock.builder()
            .addStatement("val  l =      listOf(  1,   2,3, 4)")
            .beginControlFlow("for ( x in  l  )")
            .addStatement("println(  x    )")
            .endControlFlow()
            .build()
            .formatSample()

        assertThat(code).isEqualTo(
            """
            |val l = listOf(1, 2, 3, 4)
            |for (x in l) {
            |    println(x)
            |}""".trimMargin()
        )
    }

    @Test
    fun `can survive bad formatting`() {
        val code = CodeBlock.of(" val   x =;")
        assertThat(code.format()).isEqualTo(" val   x =;")
    }
}
