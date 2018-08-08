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

import com.google.api.kotlin.config.BrandingOptions
import com.google.api.kotlin.config.ConfigurationMetadata
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.api.kotlin.types.GrpcTypes
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
internal abstract class BaseGeneratorTest {

    // accessors for the test protos
    protected val generatorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(
        javaClass.getResourceAsStream("/generate.data")
    )
    protected val testProto =
        generatorRequest.protoFileList.find { it.name == "google/example/test.proto" }
            ?: throw RuntimeException("Missing test.proto")
    protected val testTypesProto =
        generatorRequest.protoFileList.find { it.name == "google/example/test_types.proto" }
            ?: throw RuntimeException("Missing test_types.proto")
    protected val testLROProto =
        generatorRequest.protoFileList.find { it.name == "google/longrunning/test_longrunning.proto" }
            ?: throw RuntimeException("Missing test_longrunning.proto")

    // mock type mapper
    private val typesOfMessages = mapOf(
        ".google.longrunning.Operation" to ClassName("com.google.longrunning", "Operation"),
        ".$TEST_NAMESPACE.TestRequest" to ClassName(TEST_NAMESPACE, "TestRequest"),
        ".$TEST_NAMESPACE.TestResponse" to ClassName(TEST_NAMESPACE, "TestResponse"),
        ".$TEST_NAMESPACE.Result" to ClassName(TEST_NAMESPACE, "Result"),
        ".$TEST_NAMESPACE.Detail" to ClassName(TEST_NAMESPACE, "Detail"),
        ".$TEST_NAMESPACE.MoreDetail" to ClassName(TEST_NAMESPACE, "MoreDetail")
    )
    private val typesDeclaredIn = mapOf(
        ".google.longrunning.Operation" to testLROProto.messageTypeList.find { it.name == "Operation" },
        ".$TEST_NAMESPACE.TestRequest" to testProto.messageTypeList.find { it.name == "TestRequest" },
        ".$TEST_NAMESPACE.TestResponse" to testProto.messageTypeList.find { it.name == "TestResponse" },
        ".$TEST_NAMESPACE.Result" to testTypesProto.messageTypeList.find { it.name == "Result" },
        ".$TEST_NAMESPACE.Detail" to testTypesProto.messageTypeList.find { it.name == "Detail" },
        ".$TEST_NAMESPACE.MoreDetail" to testTypesProto.messageTypeList.find { it.name == "MoreDetail" })

    // a type map from the protos
    protected fun getMockedTypeMap(): ProtobufTypeMapper {
        return mock {
            on { getKotlinGrpcType(any(), any()) } doReturn ClassName(TEST_NAMESPACE, "TestStub")
            on { getKotlinGrpcType(any(), any(), any()) } doReturn ClassName(
                TEST_NAMESPACE,
                "TestStub"
            )
            on { getKotlinGrpcTypeInnerClass(any(), any(), any()) } doReturn
                ClassName(TEST_NAMESPACE, "TestStub")
            on { getKotlinGrpcTypeInnerClass(any(), any(), any(), any()) } doReturn
                ClassName(TEST_NAMESPACE, "TestStub")
            on { getKotlinType(any()) } doAnswer {
                typesOfMessages[it.arguments[0]]
                    ?: throw RuntimeException("unknown type (forget to add it?): ${it.arguments[0]}")
            }
            on { hasProtoTypeDescriptor(any()) } doAnswer {
                typesOfMessages.containsKey(it.arguments[0])
            }
            on { getProtoTypeDescriptor(any()) } doAnswer {
                typesDeclaredIn[it.arguments[0]]
                    ?: throw RuntimeException("unknown proto (forget to add it?)")
            }
        }
    }

    protected fun getMockedConfig(options: ServiceOptions): ConfigurationMetadata =
        mock {
            on { host } doReturn "my.host"
            on { scopes } doReturn listOf("scope_1", "scope_2")
            on { scopesAsLiteral } doReturn "\"scope_1\", \"scope_2\""
            on { branding } doReturn BrandingOptions("testing", "just a simple test")
            on { get(any<String>()) } doReturn options
            on { get(any<DescriptorProtos.ServiceDescriptorProto>()) } doReturn options
        }

    protected fun getMockedContext(options: ServiceOptions = ServiceOptions()): GeneratorContext {
        val config = getMockedConfig(options)
        val map = getMockedTypeMap()

        return mock {
            on { proto } doReturn generatorRequest.protoFileList.find { it.serviceCount > 0 }!!
            on { service } doReturn generatorRequest.protoFileList.find { it.serviceCount > 0 }!!.serviceList.first()
            on { metadata } doReturn config
            on { className } doReturn TEST_CLASSNAME
            on { typeMap } doReturn map
        }
    }

    // invoke the generator
    protected fun generate(options: ServiceOptions) =
        GRPCGenerator().generateServiceClient(getMockedContext(options))
}

// helpers to make code a bit shorter when dealing with names
fun messageType(name: String, packageName: String = "google.example") = ClassName(packageName, name)

fun futureCall(name: String, packageName: String = "google.example") =
    GrpcTypes.Support.FutureCall(messageType(name, packageName))

fun longRunning(name: String, packageName: String = "google.example") =
    GrpcTypes.Support.LongRunningCall(messageType(name, packageName))

fun stream(requestName: String, responseName: String, packageName: String = "google.example") =
    GrpcTypes.Support.StreamingCall(
        messageType(requestName, packageName),
        messageType(responseName, packageName)
    )

fun clientStream(
    requestName: String,
    responseName: String,
    packageName: String = "google.example"
) =
    GrpcTypes.Support.ClientStreamingCall(
        messageType(requestName, packageName),
        messageType(responseName, packageName)
    )

fun serverStream(responseName: String, packageName: String = "google.example") =
    GrpcTypes.Support.ServerStreamingCall(messageType(responseName, packageName))

// ignore indentation in tests
fun CodeBlock?.asNormalizedString(): String {
    return this?.toString()?.asNormalizedString()
        ?: throw IllegalStateException("CodeBlock cannot be null")
}

fun String?.asNormalizedString(marginPrefix: String = "|"): String {
    return this?.trimMargin(marginPrefix)
        ?.replace("(?m)^(\\s)+".toRegex(), "") // un-indent
        ?.replace("(?m)(\\s)+$".toRegex(), "") // remove trailing whitespace
        ?.replace("\n+".toRegex(), " ") // normalize newlines
        ?.replace("( ", "(")
        ?.replace(" )", ")")
        ?.trim()
        ?: throw IllegalStateException("String cannot be null")
}

internal fun List<GeneratedArtifact>.sources()= this
    .mapNotNull { it as? GeneratedSource }
    .filter { it.kind == GeneratedSource.Kind.SOURCE }

internal fun List<GeneratedArtifact>.unitTests() = this
    .mapNotNull { it as? GeneratedSource }
    .filter { it.kind == GeneratedSource.Kind.UNIT_TEST }

internal fun List<GeneratedArtifact>.firstSourceType() = this.sources().first().types.first()
internal fun List<GeneratedArtifact>.firstUnitTestType() = this.unitTests().first().types.first()

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
