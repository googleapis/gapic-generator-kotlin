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

import com.google.api.kotlin.generator.GRPCGenerator
import com.google.api.kotlin.config.BrandingOptions
import com.google.api.kotlin.config.ConfigurationMetadata
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.types.GrpcTypes
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

/**
 * Base class for generator tests that includes common plumbing for reading the protos that are used
 * and mocking up the context (see test/resources directory for protos).
 */
abstract class BaseGeneratorTest {

    // namespace of the test protos
    protected val namespace = "google.example"

    // accessors for the test protos
    protected val generatorRequest = PluginProtos.CodeGeneratorRequest.parseFrom(
            javaClass.getResourceAsStream("/generate.data"))
    protected val testProto = generatorRequest.protoFileList.find { it.name == "google/example/test.proto" }
            ?: throw RuntimeException("Missing test.proto")
    protected val testTypesProto = generatorRequest.protoFileList.find { it.name == "google/example/test_types.proto" }
            ?: throw RuntimeException("Missing test_types.proto")

    // mock type mapper
    private val typesOfMessages = mapOf(
            ".$namespace.TestRequest" to ClassName(namespace, "TestRequest"),
            ".$namespace.TestResponse" to ClassName(namespace, "TestResponse"),
            ".$namespace.Result" to ClassName(namespace, "Result"),
            ".$namespace.Detail" to ClassName(namespace, "Detail"),
            ".$namespace.MoreDetail" to ClassName(namespace, "MoreDetail"))
    private val typesDeclaredIn = mapOf(
            ".$namespace.TestRequest" to testProto.messageTypeList.find { it.name == "TestRequest" },
            ".$namespace.TestResponse" to testProto.messageTypeList.find { it.name == "TestResponse" },
            ".$namespace.Result" to testTypesProto.messageTypeList.find { it.name == "Result" },
            ".$namespace.Detail" to testTypesProto.messageTypeList.find { it.name == "Detail" },
            ".$namespace.MoreDetail" to testTypesProto.messageTypeList.find { it.name == "MoreDetail" })

    // a type map from the protos
    internal fun getMockedTypeMap(): ProtobufTypeMapper {
        return mock {
            on { getKotlinGrpcType(any(), any()) }.doReturn(ClassName(namespace, "TestStub"))
            on { getKotlinGrpcType(any(), any(), any()) }.doReturn(ClassName(namespace, "TestStub"))
            on { getKotlinGrpcTypeInnerClass(any(), any(), any()) }.doReturn(ClassName(namespace, "TestStub"))
            on { getKotlinGrpcTypeInnerClass(any(), any(), any(), any()) }.doReturn(ClassName(namespace, "TestStub"))
            on { getKotlinType(any()) }.thenAnswer {
                typesOfMessages[it.arguments[0]]
                        ?: throw RuntimeException("unknown type (forget to add it?)")
            }
            on { hasProtoTypeDescriptor(any()) }.thenAnswer {
                typesOfMessages.containsKey(it.arguments[0])
            }
            on { getProtoTypeDescriptor(any()) }.thenAnswer {
                typesDeclaredIn[it.arguments[0]] ?: throw RuntimeException("unknown proto (forget to add it?)")
            }
        }
    }

    internal fun getMockedConfig(options: ServiceOptions): ConfigurationMetadata {
        return mock {
            on { host }.doReturn("my.host")
            on { scopes }.doReturn(listOf("scope_1", "scope_2"))
            on { branding }.doReturn(BrandingOptions("testing", "just a simple test"))
            on { get(any<String>()) }.doReturn(options)
            on { get(any<DescriptorProtos.ServiceDescriptorProto>()) }.doReturn(options)
        }
    }

    // invoke the generator
    internal fun generate(options: ServiceOptions): List<GeneratedArtifact> {
        val mockedTypeMap = getMockedTypeMap()
        val mockedConfig: ConfigurationMetadata = getMockedConfig(options)

        return GRPCGenerator().generateServiceClient(mock {
            on { proto }.doReturn(generatorRequest.protoFileList.find { it.serviceCount > 0 }!!)
            on { service }.doReturn(generatorRequest.protoFileList.find { it.serviceCount > 0 }!!.serviceList.first())
            on { metadata }.doReturn(mockedConfig)
            on { className }.doReturn(ClassName(namespace, "TheTest"))
            on { typeMap }.doReturn(mockedTypeMap)
        })
    }

    // helpers to make code a bit shorter when dealing with names
    protected fun messageType(name: String) = ClassName(namespace, name)
    protected fun futureCall(name: String) = GrpcTypes.Support.FutureCall(messageType(name))
    protected fun longRunning(name: String) = GrpcTypes.Support.LongRunningCall(messageType(name))
    protected fun stream(requestName: String, responseName: String) =
            GrpcTypes.Support.StreamingCall(messageType(requestName), messageType(responseName))
    protected fun clientStream(requestName: String, responseName: String) =
            GrpcTypes.Support.ClientStreamingCall(messageType(requestName), messageType(responseName))
    protected fun serverStream(responseName: String) =
            GrpcTypes.Support.ServerStreamingCall(messageType(responseName))
}

// ignore indentation in tests
fun CodeBlock.asNormalizedString(): String {
    return this.toString().asNormalizedString()
}

fun String.asNormalizedString(marginPrefix: String = "|"): String {
    return this.trimMargin(marginPrefix).replace("(?m)^(\\s)+".toRegex(), "")
}

// non-test sources
internal fun List<GeneratedArtifact>.sources(): List<GeneratedSource> = this
    .mapNotNull { it as? GeneratedSource }
    .filter { it.kind == GeneratedSource.Kind.SOURCE }

// first non-test source
internal fun List<GeneratedArtifact>.firstSource() = this.sources().first()
internal fun List<GeneratedArtifact>.firstType() = this.sources().first().types.first()
