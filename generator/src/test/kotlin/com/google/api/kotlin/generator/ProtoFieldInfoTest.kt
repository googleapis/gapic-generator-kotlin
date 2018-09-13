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

package com.google.api.kotlin.generator

import com.google.api.kotlin.BaseGeneratorTest
import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

internal class ProtoFieldInfoTest : BaseGeneratorTest(GRPCGenerator()) {

    @Test
    fun `can find service level comments`() {
        val service = testProto.serviceList.find { it.name == "TestService" }!!
        val method = service.methodList.find { it.name == "Test" }!!

        val comments = testProto.getMethodComments(service, method)

        assertThat(comments?.trim()).isEqualTo("This is the test method")
    }

    @Test
    fun `can find parameter comments`() {
        val message = testProto.messageTypeList.find { it.name == "TestRequest" }!!
        val field = message.fieldList.find { it.name == "query" }!!
        val kotlinType = ClassName("a", "Foo")

        val fieldInfo = ProtoFieldInfo(testProto, message, field, -1, kotlinType)
        val comment = fieldInfo.file.getParameterComments(fieldInfo)

        assertThat(comment?.trim()).isEqualTo("the query")
    }
}