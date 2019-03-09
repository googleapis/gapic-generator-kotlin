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

package com.google.api.kotlin.generator

import com.google.api.kotlin.main
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.compiler.PluginProtos
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * These tests are not meant to be written by hand. The
 * gradle task "updateTestBaselines" will generate the expected output
 * during each build.
 *
 * These tests are a little silly but they test that the output of the generator
 * is the same when invoked directly vs. through protoc.
 *
 * Do not rely on them as a substitute for more fine grained tests.
 */
internal class GRPCGeneratorBaselineTest {

    @Test
    fun `can generate a client from simple_proto`() {
        check(
            input = resource("/generated-simple.data"),
            expectedOutput = resource("/baselines/testSimple.baseline.txt")
        )
    }

    @Test
    fun `can generate a client from test_proto`() {
        check(
            input = resource("/generated-test.data"),
            expectedOutput = resource("/baselines/test.baseline.txt")
        )
    }

    private fun check(input: InputStream, expectedOutput: InputStream, vararg args: String) {
        val inputFile = createTempFile("k_baseline")
        val outputDir = createTempDir("k_baseline")

        // remove the parameters in the original input request
        inputFile.outputStream().use {
            PluginProtos.CodeGeneratorRequest.parseFrom(input)
                .toBuilder()
                .setParameter("")
                .build()
                .writeTo(it)
        }

        val moreArgs = arrayOf(
            "--input=${inputFile.absolutePath}",
            "--output=${outputDir.absolutePath}",
            "--test-output=${outputDir.absolutePath}"
        )

        val resultText = try {
            System.setIn(input)
            main(arrayOf(*args) + moreArgs)
            with(StringBuilder()) {
                outputDir.walk()
                    .filter { it.isFile }
                    .sortedBy { it.absolutePath }
                    .forEach {
                        val name = it.relativeTo(outputDir).path
                        appendln("-----BEGIN:$name-----")
                        append(it.readUTF8())
                        appendln("-----END:$name-----")
                    }
                toString()
            }
        } finally {
            inputFile.delete()
            outputDir.delete()
        }

        val expectedText = expectedOutput.readUTF8()

        assertThat(resultText.isNotBlank()).isTrue()
        assertThat(expectedText.isNotBlank()).isTrue()
        assertThat(resultText).isEqualTo(expectedText)
    }

    private fun resource(path: String): InputStream = javaClass.getResourceAsStream(path)
}

private fun InputStream.readUTF8() = this.bufferedReader().use { it.readText() }
private fun File.readUTF8() = this.bufferedReader().use { it.readText() }
