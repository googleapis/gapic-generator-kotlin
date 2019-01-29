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

import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.generator.BuilderGenerator
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import mu.KotlinLogging
import org.apache.commons.text.WordUtils
import java.util.Calendar

private val log = KotlinLogging.logger {}

/**
 * Generator for Kotlin client libraries.
 *
 * @author jbolinger
 */
internal class KotlinClientGenerator(
    private val clientGenerator: ClientGenerator,
    private val clientConfigFactory: ConfigurationFactory,
    private val builderGenerator: BuilderGenerator? = null
) {

    data class Artifacts(
        val sourceCode: CodeGeneratorResponse,
        val testCode: CodeGeneratorResponse
    )

    /**
     * Generate the client from the protobuf code generation [request] and return
     * the response to forward to the protobuf code generator.
     */
    fun generate(
        request: CodeGeneratorRequest,
        typeMap: ProtobufTypeMapper,
        options: ClientPluginOptions = ClientPluginOptions(),
        filter: (DescriptorProtos.FileDescriptorProto) -> Boolean = { true }
    ): Artifacts {
        // generate code for the services
        val files = request.protoFileList
            .filter { it.serviceCount > 0 }
            .filter { request.fileToGenerateList.contains(it.name) }
            .filter { filter(it) }
            .flatMap { file ->
                file.serviceList.mapNotNull { service ->
                    try {
                        processProtoService(
                            file, service, clientConfigFactory.fromProto(file), typeMap, options
                        )
                    } catch (e: Throwable) {
                        log.error(e) { "Failed to generate client for: ${file.name}" }
                        null
                    }
                }
            }
            .flatMap { it.asIterable() }

        // extract source files
        val sourceFiles = files
            .filterIsInstance(GeneratedSource::class.java)
            .filter { it.kind == GeneratedSource.Kind.SOURCE }
            .map { toSourceFile(it) }
            .toList()

        // generate builders
        val builderFiles = builderGenerator?.let { generator ->
            generator.generate(typeMap).map { toSourceFile(it) }
        } ?: listOf()

        // put all sources together
        val sourceCode = CodeGeneratorResponse.newBuilder()
            .addAllFile(sourceFiles)
            .addAllFile(builderFiles)
            .build()

        // extract test files
        val testFiles = files
            .asSequence()
            .filterIsInstance(GeneratedSource::class.java)
            .filter { it.kind == GeneratedSource.Kind.UNIT_TEST }
            .map { toSourceFile(it) }
            .toList()

        // put all test sources together
        val testCode = CodeGeneratorResponse.newBuilder()
            .addAllFile(testFiles)
            .build()

        return Artifacts(sourceCode, testCode)
    }

    /** Process the proto file and extract services to process */
    private fun processProtoService(
        proto: DescriptorProtos.FileDescriptorProto,
        service: DescriptorProtos.ServiceDescriptorProto,
        metadata: Configuration,
        typeMap: ProtobufTypeMapper,
        options: ClientPluginOptions
    ): List<GeneratedArtifact> {
        log.debug { "processing proto: ${proto.name} -> service: ${service.name}" }

        // get package name
        val packageName = if (proto.options?.javaPackage.isNullOrBlank()) {
            proto.`package`
        } else {
            proto.options.javaPackage
        }

        // create context
        val className = ClassName(packageName, "${service.name}Client")
        val context = GeneratorContext(proto, service, metadata, className, typeMap, options)

        // generate
        return clientGenerator.generateServiceClient(context)
    }

    private fun toSourceFile(
        source: GeneratedSource,
        addLicense: Boolean = true
    ): PluginProtos.CodeGeneratorResponse.File {
        val name = source.name
        val fileSpec = FileSpec.builder(source.packageName, name)

        // add header
        if (addLicense) {
            // TODO: not sure how this will be configured long term
            val license =
                this.javaClass.getResource("/license-templates/apache-2.0.txt")?.readText()
            license?.let {
                fileSpec.addComment(
                    "%L", it
                        .replaceFirst("[yyyy]", "${Calendar.getInstance().get(Calendar.YEAR)}")
                        .replaceFirst("[name of copyright owner]", "Google LLC")
                )
            }
        }

        // add implementation
        source.types.forEach { fileSpec.addType(it) }
        source.properties.forEach { fileSpec.addProperty(it) }
        source.functions.forEach { fileSpec.addFunction(it) }
        source.imports.forEach { fileSpec.addImport(it.packageName, it.simpleName) }

        // determine file name and path
        val file = fileSpec.build()
        val fileDir = file.packageName
            .toLowerCase()
            .split(".")
            .joinToString("/")
        val fileName = "$fileDir/${file.name}.kt"

        // put it together and create file
        return PluginProtos.CodeGeneratorResponse.File.newBuilder()
            .setName(fileName)
            .setContent(file.toString())
            .build()
    }
}

/** Generator for a type of client or transport (gRPC, HTTP, etc.). */
internal interface ClientGenerator {

    /** Generate the client. */
    fun generateServiceClient(context: GeneratorContext): List<GeneratedArtifact>
}

/** Generate a configuration from a protocol buffer file. */
internal interface ConfigurationFactory {

    /** Generate a configuration */
    fun fromProto(proto: DescriptorProtos.FileDescriptorProto): Configuration
}

/** Data model for client generation */
internal class GeneratorContext(
    val proto: DescriptorProtos.FileDescriptorProto,
    val service: DescriptorProtos.ServiceDescriptorProto,
    val metadata: Configuration,
    val className: ClassName,
    val typeMap: ProtobufTypeMapper,
    val commandLineOptions: ClientPluginOptions
) {
    val serviceOptions: ServiceOptions
        get() = metadata[service]
}

internal abstract class GeneratedArtifact

/** Represents a generated source code artifact */
internal class GeneratedSource(
    val packageName: String,
    val name: String,
    val types: List<TypeSpec> = listOf(),
    val imports: List<ClassName> = listOf(),
    val properties: List<PropertySpec> = listOf(),
    val functions: List<FunSpec> = listOf(),
    val kind: Kind = Kind.SOURCE
) : GeneratedArtifact() {
    internal enum class Kind {
        SOURCE, UNIT_TEST
    }
}

/** FunSpec with additional code blocks that can be used to, optionally, generate tests. */
internal class TestableFunSpec(
    val function: FunSpec,
    val unitTestCode: CodeBlock? = null
)

/** Transforms a [FunSpec] to a [TestableFunSpec]. */
internal fun FunSpec.asTestable(unitTestCode: CodeBlock? = null) =
    TestableFunSpec(this, unitTestCode)

/** line wrapping at to the [wrapLength]. */
internal fun String?.wrap(wrapLength: Int = 100) = if (this != null) {
    WordUtils.wrap(this, wrapLength)
} else {
    null
}

/** Indent to the given [level]. */
internal fun CodeBlock.indent(level: Int): CodeBlock {
    val builder = CodeBlock.builder()
    repeat(level) { builder.indent() }
    builder.add(this)
    repeat(level) { builder.unindent() }
    return builder.build()
}
