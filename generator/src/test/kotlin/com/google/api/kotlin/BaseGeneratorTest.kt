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

import com.google.api.kotlin.config.AuthOptions
import com.google.api.kotlin.config.AuthTypes
import com.google.api.kotlin.config.BrandingOptions
import com.google.api.kotlin.config.Configuration
import com.google.api.kotlin.config.ProtobufExtensionRegistry
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.config.asPropertyPath
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

// namespaces of the test protos
private const val TEST_NAMESPACE = "google.example"
private val TEST_CLASSNAME = ClassName(TEST_NAMESPACE, "TheTest")

/**
 * Base class for generator tests that includes common plumbing for reading the protos
 * that are used for mocking the context (see test/resources directory for protos).
 *
 * Not all tests should inherit from this class (only tests that use the test protos).
 */
internal abstract class BaseGeneratorTest(
    protected val invocationOptions: ClientPluginOptions
) {
    // accessors for the test protos
    protected val generatorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(
        javaClass.getResourceAsStream("/generate.data"),
        ProtobufExtensionRegistry.INSTANCE
    ) ?: throw RuntimeException("Unable to read test data (generate.data)")

    // the primary test proto (test.proto)
    protected val proto = proto("google/example/test.proto")
    protected val services =
        generatorRequest.protoFileList.first { p ->
            p.name == "google/example/test.proto"
        }.serviceList!!
    protected val typeMap = ProtobufTypeMapper.fromProtos(generatorRequest.protoFileList)

    // extra test protos
    protected val annotationsProto = proto("google/example/test_annotations.proto")

    private fun proto(name: String) = generatorRequest.protoFileList.first { p -> p.name == name }!!
}

internal abstract class BaseClientGeneratorTest(
    private val generator: ClientGenerator,
    invocationOptions: ClientPluginOptions = ClientPluginOptions()
) : BaseGeneratorTest(invocationOptions) {

    protected fun getMockedConfig(options: ServiceOptions): Configuration =
        mock {
            on { branding } doReturn BrandingOptions("testing", "just a simple test")
            on { authentication } doReturn AuthOptions(listOf(AuthTypes.GOOGLE_CLOUD))
            on { get(any<String>()) } doReturn options
            on { get(any<DescriptorProtos.ServiceDescriptorProto>()) } doReturn options
        }

    protected fun getMockedContext(options: ServiceOptions): GeneratorContext {
        val config = getMockedConfig(options)

        return mock {
            on { proto } doReturn proto
            on { service } doReturn services.first()
            on { serviceOptions } doReturn options
            on { metadata } doReturn config
            on { className } doReturn TEST_CLASSNAME
            on { typeMap } doReturn typeMap
            on { this.commandLineOptions } doReturn invocationOptions
        }
    }

    protected fun generate(options: ServiceOptions) =
        generator.generateServiceClient(getMockedContext(options))
}

internal abstract class BaseBuilderGeneratorTest(
    private val generator: BuilderGenerator,
    invocationOptions: ClientPluginOptions = ClientPluginOptions()
) : BaseGeneratorTest(invocationOptions) {
    protected fun generate() = generator.generate(typeMap)
}

// ignore indentation in tests
fun CodeBlock?.asNormalizedString(): String {
    return this?.toString()?.asNormalizedString()
        ?: throw IllegalStateException("CodeBlock cannot be null")
}

// normalize the string to avoid checking for formatting
fun String?.asNormalizedString(marginPrefix: String = "|"): String {
    return this?.trimMargin(marginPrefix)
        ?.split("\n")
        ?.joinToString("\n") { it.trim() }
        ?.replace("\n+".toRegex(), " ") // normalize newlines
        ?.replace("( ", "(") // normalize parens
        ?.replace(" )", ")")
        ?.replace("·", " ")
        ?.trim()
        ?: throw IllegalStateException("String cannot be null")
}

internal fun props(vararg paths: String) = paths.map { it.asPropertyPath() }.toList()

internal fun List<GeneratedArtifact>.sources() = this
    .asSequence()
    .mapNotNull { it as? GeneratedSource }
    .filter { it.kind == GeneratedSource.Kind.SOURCE }
    .toList()

internal fun List<GeneratedArtifact>.unitTests() = this
    .asSequence()
    .mapNotNull { it as? GeneratedSource }
    .filter { it.kind == GeneratedSource.Kind.UNIT_TEST }
    .toList()

internal fun List<GeneratedArtifact>.testServiceClient() =
    this.sources().first { it.name == "TheTest" }.types.first()

internal fun List<GeneratedArtifact>.testServiceClientStub() =
    this.sources().first { it.name == "TheTestStub" }.types.first()

internal fun List<GeneratedSource>.kotlinBuilders(packageName: String) =
    this.filter { it.name == "KotlinBuilders" }
        .filter { it.packageName.startsWith(packageName) }

// misc. proto helpers

fun ServiceDescriptorProto(
    init: DescriptorProtos.ServiceDescriptorProto.Builder.() -> Unit
): DescriptorProtos.ServiceDescriptorProto =
    DescriptorProtos.ServiceDescriptorProto.newBuilder().apply(init).build()

fun MethodDescriptorProto(
    init: DescriptorProtos.MethodDescriptorProto.Builder.() -> Unit
): DescriptorProtos.MethodDescriptorProto =
    DescriptorProtos.MethodDescriptorProto.newBuilder().apply(init).build()

fun FileDescriptorProto(
    init: DescriptorProtos.FileDescriptorProto.Builder.() -> Unit
): DescriptorProtos.FileDescriptorProto =
    DescriptorProtos.FileDescriptorProto.newBuilder().apply(init).build()

fun DescriptorProto(
    init: DescriptorProtos.DescriptorProto.Builder.() -> Unit
): DescriptorProtos.DescriptorProto =
    DescriptorProtos.DescriptorProto.newBuilder().apply(init).build()

fun FieldDescriptorProto(
    init: DescriptorProtos.FieldDescriptorProto.Builder.() -> Unit
): DescriptorProtos.FieldDescriptorProto =
    DescriptorProtos.FieldDescriptorProto.newBuilder().apply(init).build()
