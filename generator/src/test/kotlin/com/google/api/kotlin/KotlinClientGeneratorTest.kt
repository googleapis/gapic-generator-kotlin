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
import com.google.api.kotlin.config.LegacyConfigurationFactory
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.test.Test

internal class KotlinClientGeneratorTest : BaseClientGeneratorTest(GRPCGenerator()) {

    @Test
    fun `generates a class with context`() {
        val clientGenerator: ClientGenerator = mock {
            on { generateServiceClient(any()) }.doReturn(
                listOf(
                    GeneratedSource(
                        "google.example",
                        "TestServiceClient",
                        listOf(TypeSpec.classBuilder("TestServiceClient").build())
                    )
                )
            )
        }
        val config = getMockedConfig(ServiceOptions())
        val clientConfigFactory: LegacyConfigurationFactory = mock {
            on { fromProto(any()) }.doReturn(config)
        }

        val generator = KotlinClientGenerator(clientGenerator, clientConfigFactory)
        val result = generator.generate(generatorRequest, typeMap)

        assertThat(result.sourceCode.fileCount).isEqualTo(1)
        val testClient = result.sourceCode.fileList.first { it.name == "google/example/TestServiceClient.kt" }
        assertThat(testClient.content).contains("class TestServiceClient")

        verify(clientGenerator).generateServiceClient(check {
            assertThat(it.className).isEqualTo(ClassName("google.example", "TestServiceClient"))
            assertThat(it.metadata).isEqualTo(config)
            assertThat(it.proto).isEqualTo(proto)
            assertThat(it.service).isEqualTo(proto.serviceList.find { s -> s.name == "TestService" })
        })
    }

    @Test
    fun `generates builders along with class`() {
        val clientGenerator: ClientGenerator = mock {
            on { generateServiceClient(any()) }.doReturn(
                listOf(
                    GeneratedSource(
                        "google.example",
                        "FooService",
                        listOf(TypeSpec.classBuilder("FooService").build())
                    )
                )
            )
        }
        val builderGenerator: BuilderGenerator = mock {
            on { generate(any()) }.doReturn(
                listOf(
                    GeneratedSource(
                        "foo.bar",
                        "Baz",
                        listOf(TypeSpec.classBuilder("Baz").build())
                    )
                )
            )
        }
        val config = getMockedConfig(ServiceOptions())
        val clientConfigFactory: LegacyConfigurationFactory = mock {
            on { fromProto(any()) }.doReturn(config)
        }

        val generator =
            KotlinClientGenerator(clientGenerator, clientConfigFactory, builderGenerator)
        val result = generator.generate(generatorRequest, typeMap)

        assertThat(result.sourceCode.fileCount).isEqualTo(2)
        assertThat(result.sourceCode.fileList.map { it.name }).containsExactly(
            "google/example/FooService.kt", "foo/bar/Baz.kt"
        )
    }

    @Test
    fun `can wrap text`() {
        assertThat("abcde ".repeat(1000).wrap(50)?.split("\n".toRegex())?.all { it.length <= 50 }).isTrue()
        assertThat("abcde ".wrap(50)?.trim()).isEqualTo("abcde")
        val nullString: String? = null
        assertThat(nullString.wrap()).isNull()
    }

    @Test
    fun `context uses given service options`() {
        val options: ServiceOptions = mock()
        val metadata: Configuration = mock {
            on { get(any<DescriptorProtos.ServiceDescriptorProto>()) } doReturn options
        }
        val service: DescriptorProtos.ServiceDescriptorProto = mock()

        val context = GeneratorContext(mock(), service, metadata, mock(), mock(), ClientPluginOptions())

        assertThat(context.serviceOptions).isEqualTo(options)
        verify(metadata).get(eq(service))
    }

    @Test
    fun `can handle exceptions`() {
        val clientGenerator: ClientGenerator = mock {
            on { generateServiceClient(any()) } doThrow SomeException()
        }
        val config = getMockedConfig(ServiceOptions())
        val clientConfigFactory: LegacyConfigurationFactory = mock {
            on { fromProto(any()) }.doReturn(config)
        }

        val generator = KotlinClientGenerator(clientGenerator, clientConfigFactory)
        val result = generator.generate(generatorRequest, typeMap)

        assertThat(result.sourceCode.fileCount).isEqualTo(0)
        assertThat(result.testCode.fileCount).isEqualTo(0)
    }
}

private class SomeException : RuntimeException()
