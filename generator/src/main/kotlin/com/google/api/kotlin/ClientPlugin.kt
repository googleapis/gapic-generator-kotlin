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
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.asSwappableConfiguration
import com.google.api.kotlin.generator.DSLBuilderGenerator
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.api.kotlin.types.isNotWellKnown
import com.google.devtools.common.options.OptionsParser
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import mu.KotlinLogging
import java.io.FileInputStream
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

/**
 * Main entry point.
 */
fun main(args: Array<String>) {
    // parse arguments
    val optionParser = OptionsParser.newOptionsParser(ClientPluginOptions::class.java)
    optionParser.parse(*args)
    var options = optionParser.getOptions(ClientPluginOptions::class.java)
        ?: throw IllegalStateException("Unable to parse command line options")

    // usage
    if (options.help) {
        println("Usage:")
        println(
            optionParser.describeOptions(
                mapOf(), OptionsParser.HelpVerbosity.LONG
            )
        )
        return
    }

    // parse request
    val runAsPlugin = options.inputFile.isBlank()
    val request = if (runAsPlugin) {
        CodeGeneratorRequest.parseFrom(System.`in`, ProtobufExtensionRegistry.INSTANCE)
    } else {
        FileInputStream(options.inputFile).use {
            CodeGeneratorRequest.parseFrom(it, ProtobufExtensionRegistry.INSTANCE)
        }
    }

    // parse plugin opts and override
    if (runAsPlugin) {
        log.debug { "parsing plugin options: '${request.parameter}'" }

        // override
        val pluginsOpts = request.parameter.split(",").map { "--$it" }
        val parser = OptionsParser.newOptionsParser(ClientPluginOptions::class.java)
        parser.parse(pluginsOpts)
        options = parser.getOptions(ClientPluginOptions::class.java)
            ?: throw IllegalStateException("Unable to parse plugins options")
    }
    log.info { "Using source directory: ${options.sourceDirectory}" }

    // create type map
    val typeMap = ProtobufTypeMapper.fromProtos(request.protoFileList)
    log.debug { "Discovered type: $typeMap" }

    // create & run generator
    val generator = KotlinClientGenerator(
        when {
            options.fallback -> throw RuntimeException("gRPC fallback support is not implemented")
            else -> GRPCGenerator()
        },
        options.asSwappableConfiguration(typeMap),
        DSLBuilderGenerator()
    )
    val (sourceCode, testCode) = generator.generate(request, typeMap, options) { proto ->
        if (options.includeGoogleCommon) true else proto.isNotWellKnown()
    }

    // utility for creating files
    val writeFile = { directory: String, file: PluginProtos.CodeGeneratorResponse.File ->
        val f = Paths.get(directory, file.name).toFile()
        log.debug { "creating file: ${f.absolutePath}" }

        // ensure directory exists
        if (!f.parentFile.exists()) {
            f.parentFile.mkdirs()
        }

        // ensure file is empty and write data
        if (f.exists()) {
            f.delete()
        }
        f.createNewFile()
        f.printWriter().use { it.print(file.content) }
    }

    // write source code
    try {
        if (options.outputDirectory.isBlank()) {
            sourceCode.writeTo(System.out)
        } else {
            log.info { "Writing source code output to: '${options.outputDirectory}'" }
            sourceCode.fileList.forEach { writeFile(options.outputDirectory, it) }
        }

        // write test code
        if (options.testOutputDirectory.isNotBlank()) {
            log.info { "Writing test code output to: '${options.testOutputDirectory}'" }
            testCode.fileList.forEach { writeFile(options.testOutputDirectory, it) }
        } else {
            log.warn { "Test output directory not specified. Omitted generated tests." }
        }
    } catch (e: Exception) {
        log.error(e) { "Unable to write result" }
    }
}
