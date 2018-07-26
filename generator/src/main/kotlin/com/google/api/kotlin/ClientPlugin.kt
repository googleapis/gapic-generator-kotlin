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

import com.google.api.kotlin.generator.BuilderGenerator
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.api.kotlin.generator.RetrofitGenerator
import com.google.api.kotlin.config.ConfigurationMetadataFactory
import com.google.devtools.common.options.Option
import com.google.devtools.common.options.OptionsBase
import com.google.devtools.common.options.OptionsParser
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import mu.KotlinLogging
import java.io.FileInputStream
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

/**
 * Main entry point.
 */
fun main(args: Array<String>) {
    // setup
    val optionParser = OptionsParser.newOptionsParser(CLIOptions::class.java)
    optionParser.parse(*args)
    val options = optionParser.getOptions(CLIOptions::class.java)!!

    // usage
    if (options.help) {
        println("Usage: TODO")
        println(optionParser.describeOptions(
                mapOf(), OptionsParser.HelpVerbosity.LONG))
        return
    }

    // parse request
    val runAsPlugin = options.inputFile.isNullOrBlank()
    val request = if (runAsPlugin) {
        CodeGeneratorRequest.parseFrom(System.`in`)
    } else {
        FileInputStream(options.inputFile).use {
            CodeGeneratorRequest.parseFrom(it)
        }
    }

    // parse plugin opts and override
    if (runAsPlugin) {
        log.debug { "parsing plugins options: '${request.parameter}'" }

        val pluginsOpts = request.parameter.split(",").map { "--$it" }
        val parser = OptionsParser.newOptionsParser(ProtocOptions::class.java)
        parser.parse(pluginsOpts)
        val opts = parser.getOptions(ProtocOptions::class.java)!!

        // override
        options.sourceDirectory = opts.sourceDirectory
        options.outputDirectory = opts.outputDirectory
        options.fallback = opts.fallback
    }

    // determine source dir
    val sourceDirectory = options.sourceDirectory ?: request.parameter
    log.debug { "Using source directory: $sourceDirectory" }

    // create & run generator
    val generator = KotlinClientGenerator(when {
        options.fallback -> RetrofitGenerator()
        else -> GRPCGenerator()
    }, ConfigurationMetadataFactory(sourceDirectory), BuilderGenerator())
    val result = generator.generate(request)

    // write result
    try {
        if (options.outputDirectory.isNullOrBlank()) {
            // pipe to stdout (protoc plugin)
            result.writeTo(System.out)
        } else {
            log.debug { "Writing output to: '${options.outputDirectory}'" }

            // write to disk
            result.fileList.forEach { fileDesc ->
                val f = Paths.get(options.outputDirectory, fileDesc.name).toFile()
                log.debug { "creating file: ${f.absolutePath}" }

                // ensure directory exists
                if (!f.parentFile.exists()) {
                    f.parentFile.mkdirs()
                }

                // ensure file is empty
                if (f.exists()) {
                    f.delete()
                }
                f.createNewFile()

                f.printWriter().use { it.print(fileDesc.content) }
            }
        }
    } catch (e: Exception) {
        log.error(e) { "Unable to write result" }
    }
}

open class CommonOptions : OptionsBase() {

    @JvmField
    @Option(
            name = "source_directory",
            abbrev = 's',
            help = "Source directory (proto files).",
            category = "io",
            defaultValue = "null")
    var sourceDirectory: String? = null

    @JvmField
    @Option(
            name = "output_directory",
            abbrev = 'o',
            help = "Output directory.",
            category = "io",
            defaultValue = "null")
    var outputDirectory: String? = null

    @JvmField
    @Option(
            name = "fallback",
            help = "Use gRPC fallback",
            defaultValue = "false")
    var fallback: Boolean = false
}

// CLI options
class CLIOptions : CommonOptions() {

    @JvmField
    @Option(
            name = "help",
            abbrev = 'h',
            help = "Prints usage info.",
            defaultValue = "false")
    var help = false

    @JvmField
    @Option(
            name = "input_file",
            abbrev = 'i',
            help = "Input file (code generation request proto).",
            category = "io",
            defaultValue = "null")
    var inputFile: String? = null
}

// protoc plugin options
class ProtocOptions : CommonOptions()
