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

import com.google.api.kotlin.config.ConfigurationMetadataFactory
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.generator.BuilderGenerator
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import kotlin.test.Test

internal class KotlinClientGeneratorTest : BaseGeneratorTest() {

    @Test
    fun `generates a class with context and a license`() {
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
        val clientConfigFactory: ConfigurationMetadataFactory = mock {
            on { find(any()) }.doReturn(config)
        }

        val generator = KotlinClientGenerator(clientGenerator, clientConfigFactory)
        val result = generator.generate(generatorRequest)

        assertThat(result.sourceCode.fileCount).isEqualTo(1)
        val file = result.sourceCode.fileList.first()
        assertThat(file.name).isEqualTo("google/example/TestServiceClient.kt")
        assertThat(file.content).contains("Copyright")
        assertThat(file.content).contains("class TestServiceClient")

        verify(clientGenerator).generateServiceClient(check {
            assertThat(it.className).isEqualTo(ClassName("google.example", "TestServiceClient"))
            assertThat(it.metadata).isEqualTo(config)
            val proto = generatorRequest.protoFileList.find { it.serviceCount > 0 }!!
            assertThat(it.proto).isEqualTo(proto)
            assertThat(it.service).isEqualTo(proto.serviceList.first())
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
        val clientConfigFactory: ConfigurationMetadataFactory = mock {
            on { find(any()) }.doReturn(config)
        }

        val generator =
            KotlinClientGenerator(clientGenerator, clientConfigFactory, builderGenerator)
        val result = generator.generate(generatorRequest)

        assertThat(result.sourceCode.fileCount).isEqualTo(2)
        assertThat(result.sourceCode.fileList.map { it.name }).containsExactly(
            "google/example/FooService.kt", "foo/bar/Baz.kt"
        )
        result.sourceCode.fileList.forEach {
            assertThat(it.content).contains("Copyright")
        }
    }
}
