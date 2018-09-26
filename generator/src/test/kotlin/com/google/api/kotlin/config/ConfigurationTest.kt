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

package com.google.api.kotlin.config

import com.google.api.kotlin.BaseGeneratorTest
import com.google.api.kotlin.generator.GRPCGenerator
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.fail

internal class ConfigurationTest : BaseGeneratorTest(GRPCGenerator()) {

    @Test
    fun `can use default for file level proto annotations`() {
        val factory = AnnotationConfigurationFactory(getMockedTypeMap())
        val config = factory.fromProto(testProto)

        assertThat(config.branding.name).isEqualTo("")
        assertThat(config.branding.url).isEqualTo("")
        assertThat(config.packageName).isEqualTo("google.example")
    }

    @Test
    fun `can parse file level proto annotations`() {
        val factory = AnnotationConfigurationFactory(getMockedTypeMap())
        val config = factory.fromProto(testAnnotationsProto)

        assertThat(config.branding.name).isEqualTo("The Test Product")
        assertThat(config.branding.url).isEqualTo("https://github.com/googleapis/gapic-generator-kotlin")
        assertThat(config.packageName).isEqualTo("a.name.space")
    }

    @Test
    fun `can detect a paged method`() {
        val factory = AnnotationConfigurationFactory(getMockedTypeMap())
        val config = factory.fromProto(testProto)

        val method = config["google.example.TestService"].methods.find { it.name == "PagedTest" }
        val page = method?.pagedResponse ?: fail("page is null")

        assertThat(page.pageSize).isEqualTo("page_size")
        assertThat(page.responseList).isEqualTo("responses")
        assertThat(page.requestPageToken).isEqualTo("page_token")
        assertThat(page.responsePageToken).isEqualTo("next_page_token")
    }

    @Test
    fun `skips the badly paged NotPagedTest method`() = skipsBadlyPagedMethod("NotPagedTest")

    @Test
    fun `skips the badly paged StillNotPagedTest method`() =
        skipsBadlyPagedMethod("StillNotPagedTest")

    @Test
    fun `skips the badly paged NotPagedTest2 method`() = skipsBadlyPagedMethod("NotPagedTest2")

    private fun skipsBadlyPagedMethod(methodName: String) {
        val factory = AnnotationConfigurationFactory(getMockedTypeMap())
        val config = factory.fromProto(testProto)

        val method = config["google.example.TestService"].methods.find { it.name == methodName }
        ?: fail("method not found: $methodName")

        assertThat(method.pagedResponse).isNull()
    }

    @Test
    fun `can detect method signatures`() {
        val factory = AnnotationConfigurationFactory(getMockedTypeMap())
        val config = factory.fromProto(testAnnotationsProto)

        val method = config["google.example.AnnotationService"].methods.find { it.name == "AnnotationSignatureTest" }
        val signatures = method?.flattenedMethods ?: fail("method signatures not found")

        assertThat(signatures).hasSize(1)

        assertThat(signatures[0].parameters).containsExactly(
            "foo".asPropertyPath(),
            "other.foo".asPropertyPath()
        )
    }

    @Test
    fun `does not make up method signatures`() {
        val factory = AnnotationConfigurationFactory(getMockedTypeMap())
        val config = factory.fromProto(testAnnotationsProto)

        val method = config["google.example.AnnotationService"].methods.find { it.name == "AnnotationTest" }
        val signatures = method?.flattenedMethods ?: fail("method signatures not found")

        assertThat(signatures).isEmpty()
    }
}
