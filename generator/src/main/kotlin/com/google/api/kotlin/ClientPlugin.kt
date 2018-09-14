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

import com.google.api.kotlin.config.LegacyConfigurationFactory
import com.google.api.kotlin.config.ProtobufExtensionRegistry
import com.google.api.kotlin.generator.BuilderGenerator
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.api.kotlin.generator.RetrofitGenerator
import com.google.devtools.common.options.Option
import com.google.devtools.common.options.OptionsBase
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
    val optionParser = OptionsParser.newOptionsParser(CLIOptions::class.java)
    optionParser.parse(*args)
    var options = optionParser.getOptions(CLIOptions::class.java)
        ?: throw IllegalStateException("Unable to parse options")

    // usage
    if (options.help) {
        println("Usage: TODO")
        println(
            optionParser.describeOptions(
                mapOf(), OptionsParser.HelpVerbosity.LONG
            )
        )
        return
    }

    // parse request
    val runAsPlugin = options.inputFile.isNullOrBlank()
    val request = if (runAsPlugin) {
        CodeGeneratorRequest.parseFrom(System.`in`, ProtobufExtensionRegistry.INSTANCE)
    } else {
        FileInputStream(options.inputFile).use {
            CodeGeneratorRequest.parseFrom(it, ProtobufExtensionRegistry.INSTANCE)
        }
    }

    // parse plugin opts and override
    if (runAsPlugin) {
        log.debug { "parsing plugins options: '${request.parameter}'" }

        // override
        val pluginsOpts = request.parameter.split(",").map { "--$it" }
        val parser = OptionsParser.newOptionsParser(CLIOptions::class.java)
        parser.parse(pluginsOpts)
        options = parser.getOptions(CLIOptions::class.java)
            ?: throw IllegalStateException("Unable to parse options")
    }

    // determine source dir
    val sourceDirectory = options.sourceDirectory ?: request.parameter
    log.info { "Using source directory: $sourceDirectory" }

    // create & run generator
    val generator = KotlinClientGenerator(
        when {
            options.fallback -> RetrofitGenerator()
            else -> GRPCGenerator()
        }, LegacyConfigurationFactory(sourceDirectory), BuilderGenerator()
    )
    val (sourceCode, testCode) = generator.generate(request)

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
        if (options.outputDirectory.isNullOrBlank()) {
            sourceCode.writeTo(System.out)
        } else {
            log.info { "Writing source code output to: '${options.outputDirectory}'" }
            sourceCode.fileList.forEach { writeFile(options.outputDirectory!!, it) }
        }

        // write test code
        if (options.testOutputDirectory != null) {
            log.info { "Writing test code output to: '${options.testOutputDirectory}'" }
            testCode.fileList.forEach { writeFile(options.testOutputDirectory!!, it) }
        } else {
            log.warn { "Test output directory not specified. Omitted generated tests." }
        }
    } catch (e: Exception) {
        log.error(e) { "Unable to write result" }
    }
}

// CLI options
class CLIOptions : OptionsBase() {

    @JvmField
    @Option(
        name = "help",
        abbrev = 'h',
        help = "Prints usage info.",
        defaultValue = "false"
    )
    var help = false

    @JvmField
    @Option(
        name = "input",
        abbrev = 'i',
        help = "A serialized code generation request proto (if not set it is read from stdin).",
        category = "io",
        defaultValue = "null"
    )
    var inputFile: String? = null

    @JvmField
    @Option(
        name = "output",
        abbrev = 'o',
        help = "Output directory for generated source code (if not set will be written to stdout).",
        category = "io",
        defaultValue = "null"
    )
    var outputDirectory: String? = null

    @JvmField
    @Option(
        name = "test_output",
        abbrev = 't',
        help = "Output directory for generated test code (if not set test code will be omitted).",
        category = "io",
        defaultValue = "null"
    )
    var testOutputDirectory: String? = null

    @JvmField
    @Option(
        name = "source",
        abbrev = 's',
        help = "Source directory (proto files). This option is deprecated and will be removed once the configuration process is migrated to use proto annotations.",
        category = "io",
        defaultValue = "null"
    )
    var sourceDirectory: String? = null

    @JvmField
    @Option(
        name = "fallback",
        help = "Use gRPC fallback",
        defaultValue = "false"
    )
    var fallback: Boolean = false
}
