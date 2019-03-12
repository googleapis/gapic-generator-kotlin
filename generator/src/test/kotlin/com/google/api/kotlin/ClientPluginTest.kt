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

package com.google.api.kotlin

import com.google.api.kotlin.config.ProtobufExtensionRegistry
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.compiler.PluginProtos
import org.junit.Rule
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Paths
import kotlin.test.BeforeTest
import kotlin.test.Test
import com.google.api.kotlin.main as Main

internal class ClientPluginTest {

    @Rule
    @JvmField
    val io = StdInAndOutResource()

    private lateinit var outDir: File

    // accessors for the test protos
    private val generatorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(
        javaClass.getResourceAsStream("/generated-simple.data"),
        ProtobufExtensionRegistry.INSTANCE
    )

    @BeforeTest
    fun before() {
        outDir = createTempDir()
        outDir.deleteOnExit()
    }

    @Test
    fun `is helpful`() {
        Main(arrayOf("--help"))

        assertThat(io.stdout).contains("Usage:")
        assertThat(io.stderr).isEmpty()
        assertThat(outDir.list()).isEmpty()
    }

    @Test
    fun `can generate a simple proto in protoc plugin mode`() {
        io.stdIn = ByteArrayInputStream(generatorRequest.toByteArray())

        Main(arrayOf())

        verifyResponse(PluginProtos.CodeGeneratorResponse.parseFrom(io.stdoutBytes))

        assertThat(outDir.list()).isEmpty()
    }

    @Test
    fun `can generate tests in protoc plugin mode`() {
        val requestBuilder = generatorRequest.toBuilder()
        requestBuilder.parameter = "test-output=${outDir.absolutePath}"

        io.stdIn = ByteArrayInputStream(requestBuilder.build().toByteArray())

        Main(arrayOf())

        verifyResponse(PluginProtos.CodeGeneratorResponse.parseFrom(io.stdoutBytes))

        verifyTests(outDir)
    }

    @Test
    fun `can generate a simple proto in standalone mode`() {
        val inputFile = createTempFile()
        inputFile.deleteOnExit()
        inputFile.writeBytes(generatorRequest.toByteArray())

        Main(arrayOf("--input", inputFile.absolutePath))

        verifyResponse(PluginProtos.CodeGeneratorResponse.parseFrom(io.stdoutBytes))
    }

    @Test
    fun `can generate a tests proto in standalone mode`() {
        val inputFile = createTempFile()
        inputFile.deleteOnExit()
        inputFile.writeBytes(generatorRequest.toByteArray())

        Main(arrayOf("--input", inputFile.absolutePath, "--test-output", outDir.absolutePath))

        verifyResponse(PluginProtos.CodeGeneratorResponse.parseFrom(io.stdoutBytes))
        verifyTests(outDir)
    }

    @Test
    fun `can generate a simple proto to disk in standalone mode`() {
        val inputFile = createTempFile()
        inputFile.deleteOnExit()
        inputFile.writeBytes(generatorRequest.toByteArray())

        Main(arrayOf("--input", inputFile.absolutePath, "--output", outDir.absolutePath))

        verifyResponse(outDir)
    }

    private fun verifyResponse(response: PluginProtos.CodeGeneratorResponse) {
        assertThat(response.fileCount).isEqualTo(3)

        val client = response.fileList.first { it.name == "google/example/SimpleServiceClient.kt" }.content
        val clientStub = response.fileList.first { it.name == "google/example/SimpleServiceClientStub.kt" }.content
        val builders = response.fileList.first { it.name == "google/example/KotlinBuilders.kt" }.content

        verifyResponse(client, clientStub, builders)
    }

    private fun verifyResponse(directory: File) {
        val files = directory.walk()
            .filter { it.isFile }
            .map { it.absoluteFile }
            .toList()

        assertThat(files).hasSize(3)

        val client = files.first { it.path.endsWith("google/example/SimpleServiceClient.kt") }.readText()
        val clientStub = files.first { it.path.endsWith("google/example/SimpleServiceClientStub.kt") }.readText()
        val builders = files.first { it.path.endsWith("google/example/KotlinBuilders.kt") }.readText()

        verifyResponse(client, clientStub, builders)
    }

    private fun verifyResponse(client: String, clientStub: String, builders: String) {
        for (file in listOf(client, clientStub)) {
            assertThat(file).contains("@Generated(\"${GRPCGenerator::class.qualifiedName}\")")
        }

        assertThat(client).contains("class SimpleServiceClient")
        assertThat(clientStub).contains("class SimpleServiceClientStub")
        assertThat(builders).contains("inline class SimpleRequestDsl")
        assertThat(builders).contains("inline class SimpleResponseDsl")
        assertThat(builders).contains("fun simpleRequest(init: SimpleRequestDsl.() -> Unit): SimpleRequest")
        assertThat(builders).contains("fun simpleResponse(init: SimpleResponseDsl.() -> Unit): SimpleResponse")
    }

    private fun verifyTests(directory: File) {
        assertThat(directory.isDirectory).isTrue()

        val files = directory.walk()
            .filter { it.isFile }
            .map { it.absolutePath }
            .toList()

        assertThat(files).containsExactly(
            Paths.get(
                directory.absolutePath, "google", "example", "SimpleServiceClientTest.kt"
            ).toAbsolutePath().toString()
        )
    }
}