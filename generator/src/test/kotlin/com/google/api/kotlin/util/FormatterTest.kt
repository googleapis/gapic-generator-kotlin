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
import com.google.protobuf.compiler.PluginProtos
import kotlin.test.Test

internal class FormatterTest {
    @Test
    fun `can format code`() {
        val response = response {
            addSource("foo.kt", "fun foo(x:     Int)       =    2")
            addSource("bar.kt", "fun   bar(y:  String) =  \"hi\"  ")
        }

        val formatted = response.format()
        assertThat(formatted.fileList).containsExactly(
            source("foo.kt", "fun foo(x: Int) = 2"),
            source("bar.kt", "fun bar(y: String) = \"hi\"")
        )
    }

//    @Test
//    fun `can format non-top-level sample code`() {
//        val code = CodeBlock.builder()
//            .addStatement("val  l =      listOf(  1,   2,3, 4)")
//            .beginControlFlow("for ( x in  l  )")
//            .addStatement("println(  x    )")
//            .endControlFlow()
//            .build()
//            .formatSample()
//
//        assertThat(code).isEqualTo(
//            """
//            |val l = listOf(1, 2, 3, 4)
//            |for (x in l) {
//            |    println(x)
//            |}""".trimMargin()
//        )
//    }
}

private fun response(init: PluginProtos.CodeGeneratorResponse.Builder.() -> Unit) =
    with(PluginProtos.CodeGeneratorResponse.newBuilder()) {
        apply(init)
        build()
    }

private fun PluginProtos.CodeGeneratorResponse.Builder.addSource(name: String, content: String) =
    this.addFile(source(name, content))

private fun source(name: String, content: String) =
    with(PluginProtos.CodeGeneratorResponse.File.newBuilder()) {
        this.name = name
        this.content = content
        build()
    }
