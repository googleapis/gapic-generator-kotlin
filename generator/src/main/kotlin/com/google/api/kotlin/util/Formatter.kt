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

import com.google.protobuf.compiler.PluginProtos
import com.squareup.kotlinpoet.CodeBlock
import mu.KotlinLogging
import java.io.File

// ktlint doesn't work well when embedded in projects
// so we have to resort to running the jar
//
// https://github.com/shyiko/ktlint/issues/340

private val log = KotlinLogging.logger {}

// TODO: placeholder
internal fun CodeBlock.formatSample() = this
// internal fun CodeBlock.formatSample(): String {
//    val code = CodeBlock.of("fun example() { \n%L\n }", this)
//
//    return code.format()
//        .replace(Regex("\\A\\s*fun\\s+example\\s*\\(\\)\\s*\\{\\s*"), "")
//        .replace(Regex("\\s*\\}\\s*\\z"), "")
// }

// this is not so great since we need to spin up new processes
private object Formatter {
    val jar = Formatter::class.java.getResourceAsStream("/ktlint").use {
        val file = createTempFile()
        file.deleteOnExit()
        file.writeBytes(it.readBytes())
        file
    }
}

/** Formats all the code in a response using a temporary directory */
internal fun PluginProtos.CodeGeneratorResponse.format(): PluginProtos.CodeGeneratorResponse {
    val newRequest = this.toBuilder().clearFile()

    try {
        val directory = createTempDir()
        directory.deleteOnExit()

        val filesToUpdate = this.fileList.map { file ->
            val tmp = createTempFile(directory = directory)
            tmp.bufferedWriter().use { it.write(file.content) }
            Pair(file, tmp)
        }

        val java = listOf(System.getProperty("java.home"), "bin", "java").joinToString(File.separator)
        val args = mutableListOf("--format")
        // TODO: add --android option when using lite

        args += directory.absolutePath
        val builder = ProcessBuilder(
            java, "-jar", Formatter.jar.absolutePath,
            *args.toTypedArray(),
            directory.absolutePath
        )
        val process = builder.start()
        process.waitFor()

        for ((oldFile, newFile) in filesToUpdate) {
            newRequest.addFile(
                oldFile.toBuilder()
                    .setContent(newFile.bufferedReader().use { it.readText() })
                    .build()
            )
        }
    } catch (t: Throwable) {
        log.error(t) { "Code could not be formatted (using original)" }
    }

    return newRequest.build()
}
