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

import com.google.api.kotlin.BaseClientGeneratorTest
import com.google.common.truth.Truth.assertThat
import com.google.rpc.Code
import kotlin.test.Test
import kotlin.test.fail

internal class ConfigurationTest : BaseClientGeneratorTest("test_annotations", "AnnotationServiceClient") {

    @Test
    fun `can use default for file level proto annotations`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        // assertThat(config.branding.name).isEqualTo("")
        assertThat(config.packageName).isEqualTo("google.example")
    }

    @Test
    fun `can parse file level proto annotations`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        // assertThat(config.branding.name).isEqualTo("The Test Product")
        assertThat(config.packageName).isEqualTo("google.example")
    }

    @Test
    fun `can detected a long running method`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        val method = config["google.example.AnnotationService"].methods.first { it.name == "AnnotationLongRunningTest" }
        val operation = method.longRunningResponse ?: fail("long running response is null")

        assertThat(operation.responseType).isEqualTo(".google.example.TheLongRunningResponse")
        assertThat(operation.metadataType).isEqualTo(".google.example.TheLongRunningMetadata")
    }

    @Test
    fun `can detect method signatures`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        val method = config["google.example.AnnotationService"].methods.find { it.name == "AnnotationSignatureTest" }
        val signatures = method?.flattenedMethods ?: fail("method signatures not found")

        assertThat(signatures).hasSize(2)

        assertThat(signatures[0].parameters).containsExactly(
            "foo".asPropertyPath()
        ).inOrder()
        assertThat(signatures[1].parameters).containsExactly(
            "foo".asPropertyPath(),
            "other.foo".asPropertyPath()
        ).inOrder()
    }

    @Test
    fun `does not make up method signatures`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        val method = config["google.example.AnnotationService"].methods.find { it.name == "AnnotationTest" }
        val signatures = method?.flattenedMethods ?: fail("method signatures not found")

        assertThat(signatures).isEmpty()
    }

//    @Test
//    fun `can detect retry settings`() {
//        val factory = AnnotationConfigurationFactory(AuthOptions(), getMockedTypeMap())
//        val config = factory.fromProto(testAnnotationsProto)
//
//        val method = config["google.example.AnnotationService"].methods.first { it.name == "AnnotationRetryTest" }
//
//        assertThat(method.retry).isNotNull()
//        assertThat(method.retry!!.codes).containsExactly(Code.UNKNOWN, Code.NOT_FOUND).inOrder()
//    }

    @Test
    fun `can detect default retry settings`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        val method =
            config["google.example.AnnotationService"].methods.first { it.name == "AnnotationRetryDefaultTest" }

        assertThat(method.retry).isNotNull()
        assertThat(method.retry!!.codes).containsExactly(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED)
    }

    @Test
    fun `does not make up retry settings`() {
        val factory = AnnotationConfigurationFactory(AuthOptions(), typeMap)
        val config = factory.fromProto(proto)

        val method = config["google.example.AnnotationService"].methods.first { it.name == "AnnotationTest" }
        assertThat(method.retry).isNull()
    }
}
