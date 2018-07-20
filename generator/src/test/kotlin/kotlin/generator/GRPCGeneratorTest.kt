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

import com.google.api.kotlin.generator.config.FlattenedMethod
import com.google.api.kotlin.generator.config.MethodOptions
import com.google.api.kotlin.generator.config.ServiceOptions
import com.google.api.kotlin.generator.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.Test

class GRPCGeneratorTest : BaseGeneratorTest() {

    @Test
    fun `Generates with class documentation`() {
        val opts = ServiceOptions(listOf())

        assertThat(generate(opts).type.kdoc.toString()).isNotEmpty()
    }

    @Test
    fun `Generates with required static imports`() {
        val opts = ServiceOptions(listOf())

        val imports = generate(opts).imports
        assertThat(imports).containsExactly(
                ClassName(GrpcTypes.Support.SUPPORT_LIB_PACKAGE, "pager"),
                ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "decorate"),
                ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "prepare"))
    }

    @Test
    fun `Generates the test method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "Test")))

        val methods = generate(opts).type.funSpecs.filter { it.name == "test" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(futureCall("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("request")
        assertThat(method.parameters.first().type).isEqualTo(messageType("TestRequest"))
        assertThat(method.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.test(request)
                |}
            """.asNormalizedString())
    }

    @Test
    fun `Generates the LRO test method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "OperationTest")))

        val methods = generate(opts).type.funSpecs.filter { it.name == "operationTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(longRunning("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("request")
        assertThat(method.parameters.first().type).isEqualTo(messageType("TestRequest"))
        assertThat(method.body.asNormalizedString()).isEqualTo("""
                |return ${longRunning("TestResponse")}(
                |  stubs.operation,
                |  stubs.future.prepare(options).executeFuture {
                |    it.operationTest(request)
                |  }, ${messageType("TestResponse")}::class.java)
            """.asNormalizedString())
    }

    @Test
    fun `Generates the streamTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamTest")))

        val methods = generate(opts).type.funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(stream("TestRequest", "TestResponse"))
        assertThat(method.parameters).isEmpty()
        assertThat(method.body.asNormalizedString()).isEqualTo("""
                |return stubs.stream.prepare(options).executeStreaming { it::streamTest }
            """.asNormalizedString())
    }

    @Test
    fun `Generates the streamClientTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamTest")))

        val methods = generate(opts).type.funSpecs.filter { it.name == "streamClientTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(clientStream("TestRequest", "TestResponse"))
        assertThat(method.parameters).isEmpty()
        assertThat(method.body.asNormalizedString()).isEqualTo("""
                |return stubs.stream.prepare(options).executeClientStreaming { it::streamClientTest }
            """.asNormalizedString())
    }

    @Test
    fun `Generates the streamServerTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamTest")))

        val methods = generate(opts).type.funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(serverStream("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("request")
        assertThat(method.parameters.first().type).isEqualTo(messageType("TestRequest"))
        assertThat(method.body.asNormalizedString()).isEqualTo("""
                |return stubs.stream.prepare(options).executeServerStreaming { stub, observer ->
                |  stub.streamServerTest(request, observer)
                |}
            """.asNormalizedString())
    }

    @Test
    fun `Generates the testFlat methods`() {
        val opts = ServiceOptions(listOf(
                MethodOptions(name = "TestFlat",
                        flattenedMethods = listOf(
                                FlattenedMethod(listOf("query")),
                                FlattenedMethod(listOf("query", "main_detail"))),
                        keepOriginalMethod = true)))

        val methods = generate(opts).type.funSpecs.filter { it.name == "testFlat" }
        assertThat(methods).hasSize(3)
        assertThat(methods.map { it.returnType }).containsExactly(
                futureCall("TestResponse"),
                futureCall("TestResponse"),
                futureCall("TestResponse"))

        val original = methods.find { it.parameters.size == 1 && it.parameters[0].name == "request" }
        assertThat(original).isNotNull()
        original?.apply {
            assertThat(parameters.first().name).isEqualTo("request")
            assertThat(parameters.first().type).isEqualTo(messageType("TestRequest"))
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.testFlat(request)
                |}
            """.asNormalizedString())
        }

        val oneArg = methods.find { it.parameters.size == 1 && it.parameters[0].name != "request" }
        assertThat(oneArg).isNotNull()
        oneArg?.apply {
            assertThat(parameters.first().name).isEqualTo("query")
            assertThat(parameters.first().type).isEqualTo(String::class.asTypeName())
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.testFlat($namespace.TestRequest.newBuilder()
                |    .setQuery(query)
                |    .build())
                |}
            """.asNormalizedString())
        }

        val twoArg = methods.find { it.parameters.size == 2 }
        assertThat(twoArg).isNotNull()
        twoArg?.apply {
            assertThat(parameters[0].name).isEqualTo("query")
            assertThat(parameters[0].type).isEqualTo(String::class.asTypeName())
            assertThat(parameters[1].name).isEqualTo("mainDetail")
            assertThat(parameters[1].type).isEqualTo(messageType("Detail"))
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.testFlat($namespace.TestRequest.newBuilder()
                |    .setQuery(query)
                |    .setMainDetail(mainDetail)
                |    .build())
                |}
            """.asNormalizedString())
        }
    }

    @Test
    fun `Generates the TestFlatWithoutOriginal methods`() {
        val opts = ServiceOptions(listOf(
                MethodOptions(name = "TestFlatWithoutOriginal",
                        flattenedMethods = listOf(
                                FlattenedMethod(listOf("main_detail"))),
                        keepOriginalMethod = false)))

        val methods = generate(opts).type.funSpecs.filter { it.name == "testFlatWithoutOriginal" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val oneArg = methods.find { it.parameters.size == 1 }
        assertThat(oneArg).isNotNull()
        oneArg?.apply {
            assertThat(parameters.first().name).isEqualTo("mainDetail")
            assertThat(parameters.first().type).isEqualTo(messageType("Detail"))
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.testFlatWithoutOriginal($namespace.TestRequest.newBuilder()
                |    .setMainDetail(mainDetail)
                |    .build())
                |}
            """.asNormalizedString())
        }
    }

    @Test
    fun `Generates the NestedFlat methods`() {
        val opts = ServiceOptions(listOf(
                MethodOptions(name = "NestedFlat",
                        flattenedMethods = listOf(
                                FlattenedMethod(listOf("main_detail.even_more"))),
                        keepOriginalMethod = false)))

        val methods = generate(opts).type.funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("evenMore")
            assertThat(parameters.first().type).isEqualTo(messageType("MoreDetail"))
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.nestedFlat($namespace.TestRequest.newBuilder()
                |    .setMainDetail($namespace.Detail.newBuilder()
                |      .setEvenMore(evenMore)
                |      .build()
                |    )
                |    .build())
                |}
            """.asNormalizedString())
        }
    }

    @Test
    fun `Generates the NestedFlat methods with repeated fields`() {
        val opts = ServiceOptions(listOf(
                MethodOptions(name = "NestedFlat",
                        flattenedMethods = listOf(
                                FlattenedMethod(listOf("more_details"))),
                        keepOriginalMethod = false)))

        val methods = generate(opts).type.funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("moreDetails")
            assertThat(parameters.first().type).isEqualTo(ParameterizedTypeName.get(
                    List::class.asTypeName(), messageType("Detail")))
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.nestedFlat($namespace.TestRequest.newBuilder()
                |    .addAllMoreDetails(moreDetails)
                |    .build())
                |}
            """.asNormalizedString())
        }
    }

    @Test
    fun `Generates the NestedFlat methods with repeated nested fields`() {
        val opts = ServiceOptions(listOf(
                MethodOptions(name = "NestedFlat",
                        flattenedMethods = listOf(
                                FlattenedMethod(listOf("more_details[0].even_more"))),
                        keepOriginalMethod = false)))

        val methods = generate(opts).type.funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("evenMore")
            assertThat(parameters.first().type).isEqualTo(messageType("MoreDetail"))
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.nestedFlat($namespace.TestRequest.newBuilder()
                |    .addMoreDetails($namespace.Detail.newBuilder()
                |      .setEvenMore(evenMore)
                |      .build()
                |    )
                |    .build())
                |}
            """.asNormalizedString())
        }
    }

    @Test
    fun `Generates the NestedFlatPrimitive methods`() {
        val opts = ServiceOptions(listOf(
                MethodOptions(name = "NestedFlatPrimitive",
                        flattenedMethods = listOf(
                                FlattenedMethod(listOf("main_detail.useful"))),
                        keepOriginalMethod = false)))

        val methods = generate(opts).type.funSpecs.filter { it.name == "nestedFlatPrimitive" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("useful")
            assertThat(parameters.first().type).isEqualTo(Boolean::class.asTypeName())
            assertThat(this.body.asNormalizedString()).isEqualTo("""
                |return stubs.future.prepare(options).executeFuture {
                |  it.nestedFlatPrimitive($namespace.TestRequest.newBuilder()
                |    .setMainDetail($namespace.Detail.newBuilder()
                |      .setUseful(useful)
                |      .build()
                |    )
                |    .build())
                |}
            """.asNormalizedString())
        }
    }

}