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
import com.nhaarman.mockito_kotlin.doAnswer
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
internal abstract class BaseGeneratorTest(private val generator: ClientGenerator) {

    // accessors for the test protos
    protected val generatorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(
        javaClass.getResourceAsStream("/generate.data"),
        ProtobufExtensionRegistry.INSTANCE
    )

    protected val testProto =
        generatorRequest.protoFileList.find { it.name == "google/example/test.proto" }
            ?: throw RuntimeException("Missing test.proto")
    protected val testTypesProto =
        generatorRequest.protoFileList.find { it.name == "google/example/test_types.proto" }
            ?: throw RuntimeException("Missing test_types.proto")
    protected val testLongrunningProto =
        generatorRequest.protoFileList.find { it.name == "google/longrunning/operations.proto" }
            ?: throw RuntimeException("Missing test_types.proto")
    protected val testAnnotationsProto =
        generatorRequest.protoFileList.find { it.name == "google/example/test_annotations.proto" }
            ?: throw RuntimeException("Missing test_annotations.proto")

    private val protoMessageTypes = listOf("Empty", "Any")
    private val operationMessageTypes = listOf(
        "Operation",
        "GetOperationRequest", "GetOperationResponse",
        "ListOperationsRequest", "ListOperationsResponse",
        "CancelOperationRequest", "DeleteOperationRequest",
        "OperationTypes"
    )
    private val testMessageTypes = listOf(
        "TestRequest", "TestResponse",
        "PagedRequest", "PagedResponse", "NotPagedRequest", "NotPagedResponse",
        "StillNotPagedResponse",
        "SomeResponse", "SomeMetadata"
    )
    private val testMessageMoreTypes = listOf("Result", "Detail", "MoreDetail")
    private val annotationMessageTypes = listOf("FooRequest", "BarResponse")

    // mock type mapper
    private val typesOfMessages =
        (testMessageTypes + testMessageMoreTypes + annotationMessageTypes).associate {
            ".$TEST_NAMESPACE.$it" to ClassName(TEST_NAMESPACE, it)
        } + protoMessageTypes.associate {
            ".google.protobuf.$it" to ClassName("com.google.protobuf", it)
        } + operationMessageTypes.associate {
            ".google.longrunning.$it" to ClassName("com.google.longrunning", it)
        }

    private val typesDeclaredIn = testMessageTypes.associate {
        ".$TEST_NAMESPACE.$it" to testProto.messageTypeList.find { m -> m.name == it }
    } + testMessageMoreTypes.associate {
        ".$TEST_NAMESPACE.$it" to testTypesProto.messageTypeList.find { m -> m.name == it }
    } + annotationMessageTypes.associate {
        ".$TEST_NAMESPACE.$it" to testAnnotationsProto.messageTypeList.find { m -> m.name == it }
    } + protoMessageTypes.associate {
        ".google.protobuf.$it" to testAnnotationsProto.messageTypeList.find { m -> m.name == it }
    } + operationMessageTypes.associate {
        ".google.longrunning.$it" to testLongrunningProto.messageTypeList.find { m -> m.name == it }
    }

    // a type map from the protos
    protected fun getMockedTypeMap(): ProtobufTypeMapper {
        return mock {
            on { getKotlinType(any()) } doAnswer {
                typesOfMessages[it.arguments[0]]
                    ?: throw RuntimeException("unknown type (forget to add it?): ${it.arguments[0]}")
            }
            on { hasProtoTypeDescriptor(any()) } doAnswer {
                typesOfMessages.containsKey(it.arguments[0])
            }
            on { getProtoTypeDescriptor(any()) } doAnswer {
                typesDeclaredIn[it.arguments[0]]
                    ?: throw RuntimeException("unknown proto (forget to add it: ${it.arguments[0]}?)")
            }
        }
    }

    protected fun getMockedConfig(options: ServiceOptions): Configuration =
        mock {
            on { branding } doReturn BrandingOptions("testing", "just a simple test")
            on { authentication } doReturn AuthOptions(listOf(AuthTypes.GOOGLE_CLOUD))
            on { get(any<String>()) } doReturn options
            on { get(any<DescriptorProtos.ServiceDescriptorProto>()) } doReturn options
        }

    protected fun getMockedContext(options: ServiceOptions): GeneratorContext {
        val config = getMockedConfig(options)
        val map = getMockedTypeMap()

        return mock {
            on { proto } doReturn generatorRequest.protoFileList.find { p -> p.name == "google/example/test.proto" }!!
            on { service } doReturn generatorRequest.protoFileList.find { p -> p.name == "google/example/test.proto" }!!.serviceList.first()
            on { serviceOptions } doReturn options
            on { metadata } doReturn config
            on { className } doReturn TEST_CLASSNAME
            on { typeMap } doReturn map
        }
    }

    // invoke the generator
    protected fun generate(options: ServiceOptions) =
        generator.generateServiceClient(getMockedContext(options))
}

// helpers to make code a bit shorter when dealing with names
fun messageType(name: String, packageName: String = "google.example") = ClassName(packageName, name)

// ignore indentation in tests
fun CodeBlock?.asNormalizedString(): String {
    return this?.toString()?.asNormalizedString()
        ?: throw IllegalStateException("CodeBlock cannot be null")
}

// normalize the string to avoid checking for formatting
fun String?.asNormalizedString(marginPrefix: String = "|"): String {
    return this?.trimMargin(marginPrefix)
        ?.split("\n")
        ?.map { it.trim() } // un-indent and normalize whitespace
        ?.joinToString("\n")
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
