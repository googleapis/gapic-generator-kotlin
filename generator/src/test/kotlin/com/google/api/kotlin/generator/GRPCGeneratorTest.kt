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
import com.google.api.kotlin.TEST_CLASSNAME
import com.google.api.kotlin.TEST_NAMESPACE
import com.google.api.kotlin.TEST_NAMESPACE_KGAX
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.clientStream
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.firstSource
import com.google.api.kotlin.firstType
import com.google.api.kotlin.futureCall
import com.google.api.kotlin.longRunning
import com.google.api.kotlin.messageType
import com.google.api.kotlin.serverStream
import com.google.api.kotlin.stream
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.Test

internal class GRPCGeneratorTest : BaseGeneratorTest() {

    @Test
    fun `Generates with class documentation`() {
        val opts = ServiceOptions(listOf())

        assertThat(generate(opts).firstType().kdoc.toString()).isNotEmpty()
    }

    @Test
    fun `Generates with prepare`() {
        val opts = ServiceOptions(listOf())

        val methods = generate(opts).firstType().funSpecs

        val method = methods.first { it.name == "prepare" }
        assertThat(method.returnType).isEqualTo(TEST_CLASSNAME)
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("init")
        assertThat(method.parameters.first().type).isEqualTo(
            LambdaTypeName.get(
                GrpcTypes.Support.ClientCallOptionsBuilder,
                listOf(),
                Unit::class.asTypeName()
            )
        )
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |val options = $TEST_NAMESPACE_KGAX.grpc.ClientCallOptions.Builder(options)
                |options.init()
                |return ${TEST_CLASSNAME.packageName}.${TEST_CLASSNAME.simpleName}(channel, options.build())
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates with required static imports`() {
        val opts = ServiceOptions(listOf())

        val imports = generate(opts).firstSource().imports
        assertThat(imports).containsExactly(
            ClassName(GrpcTypes.Support.SUPPORT_LIB_PACKAGE, "pager"),
            ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "prepare")
        )
    }

    @Test
    fun `Generates the test method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "Test")))

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "test" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(futureCall("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("request")
        assertThat(method.parameters.first().type).isEqualTo(messageType("TestRequest"))
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |return stubs.future.executeFuture {
                |  it.test(request)
                |}
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the LRO test method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "OperationTest")))

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "operationTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(longRunning("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("request")
        assertThat(method.parameters.first().type).isEqualTo(messageType("TestRequest"))
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |return ${longRunning("TestResponse")}(
                |  stubs.operation,
                |  stubs.future.executeFuture {
                |    it.operationTest(request)
                |  }, ${messageType("TestResponse")}::class.java)
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamTest")))

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(stream("TestRequest", "TestResponse"))
        assertThat(method.parameters).isEmpty()
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |return stubs.stream.executeStreaming { it::streamTest }
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamTest method with flattening`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "StreamTest",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("query")),
                        FlattenedMethod(listOf("query", "main_detail"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(2)

        val oneParamMethod = methods.first { it.parameters.size == 1 }
        assertThat(oneParamMethod.returnType).isEqualTo(stream("TestRequest", "TestResponse"))
        assertThat(oneParamMethod.parameters[0].name).isEqualTo("query")
        assertThat(oneParamMethod.parameters[0].type).isEqualTo(String::class.asTypeName())
        assertThat(oneParamMethod.body.asNormalizedString()).isEqualTo(
            """
                |val stream = stubs.stream.executeStreaming { it::streamTest }
                |stream.requests.send($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setQuery(query)
                |    .build())
                |return stream
            """.asNormalizedString()
        )

        val twoParamMethod = methods.first { it.parameters.size == 2 }
        assertThat(twoParamMethod.returnType).isEqualTo(stream("TestRequest", "TestResponse"))
        assertThat(twoParamMethod.parameters[0].name).isEqualTo("query")
        assertThat(twoParamMethod.parameters[0].type).isEqualTo(String::class.asTypeName())
        assertThat(twoParamMethod.parameters[1].name).isEqualTo("mainDetail")
        assertThat(twoParamMethod.parameters[1].type).isEqualTo(messageType("Detail"))
        assertThat(twoParamMethod.body.asNormalizedString()).isEqualTo(
            """
                |val stream = stubs.stream.executeStreaming { it::streamTest }
                |stream.requests.send($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setQuery(query)
                |    .setMainDetail(mainDetail)
                |    .build())
                |return stream
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamClientTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamClientTest")))

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "streamClientTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(clientStream("TestRequest", "TestResponse"))
        assertThat(method.parameters).isEmpty()
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |return stubs.stream.executeClientStreaming { it::streamClientTest }
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamServerTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamServerTest")))

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(serverStream("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("request")
        assertThat(method.parameters.first().type).isEqualTo(messageType("TestRequest"))
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |return stubs.stream.executeServerStreaming { stub, observer ->
                |  stub.streamServerTest(request, observer)
                |}
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamServerTest method with flattening`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "StreamServerTest",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(serverStream("TestResponse"))
        assertThat(method.parameters).hasSize(1)
        assertThat(method.parameters.first().name).isEqualTo("evenMore")
        assertThat(method.parameters.first().type).isEqualTo(messageType("MoreDetail"))
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
                |return stubs.stream.executeServerStreaming { stub, observer ->
                |    stub.streamServerTest($TEST_NAMESPACE.TestRequest.newBuilder()
                |        .setMainDetail($TEST_NAMESPACE.Detail.newBuilder()
                |            .setEvenMore(evenMore)
                |            .build()
                |    )
                |    .build(), observer)
                |}
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the testFlat methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "TestFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("query")),
                        FlattenedMethod(listOf("query", "main_detail"))
                    ),
                    keepOriginalMethod = true
                )
            )
        )

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "testFlat" }
        assertThat(methods).hasSize(3)
        assertThat(methods.map { it.returnType }).containsExactly(
            futureCall("TestResponse"),
            futureCall("TestResponse"),
            futureCall("TestResponse")
        )

        val original =
            methods.find { it.parameters.size == 1 && it.parameters[0].name == "request" }
        assertThat(original).isNotNull()
        original?.apply {
            assertThat(parameters.first().name).isEqualTo("request")
            assertThat(parameters.first().type).isEqualTo(messageType("TestRequest"))
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.testFlat(request)
                |}
            """.asNormalizedString()
            )
        }

        val oneArg = methods.find { it.parameters.size == 1 && it.parameters[0].name != "request" }
        assertThat(oneArg).isNotNull()
        oneArg?.apply {
            assertThat(parameters.first().name).isEqualTo("query")
            assertThat(parameters.first().type).isEqualTo(String::class.asTypeName())
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.testFlat($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setQuery(query)
                |    .build())
                |}
            """.asNormalizedString()
            )
        }

        val twoArg = methods.find { it.parameters.size == 2 }
        assertThat(twoArg).isNotNull()
        twoArg?.apply {
            assertThat(parameters[0].name).isEqualTo("query")
            assertThat(parameters[0].type).isEqualTo(String::class.asTypeName())
            assertThat(parameters[1].name).isEqualTo("mainDetail")
            assertThat(parameters[1].type).isEqualTo(messageType("Detail"))
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.testFlat($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setQuery(query)
                |    .setMainDetail(mainDetail)
                |    .build())
                |}
            """.asNormalizedString()
            )
        }
    }

    @Test
    fun `Generates the TestFlatWithoutOriginal methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "TestFlatWithoutOriginal",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("main_detail"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods =
            generate(opts).firstType().funSpecs.filter { it.name == "testFlatWithoutOriginal" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val oneArg = methods.find { it.parameters.size == 1 }
        assertThat(oneArg).isNotNull()
        oneArg?.apply {
            assertThat(parameters.first().name).isEqualTo("mainDetail")
            assertThat(parameters.first().type).isEqualTo(messageType("Detail"))
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.testFlatWithoutOriginal($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setMainDetail(mainDetail)
                |    .build())
                |}
            """.asNormalizedString()
            )
        }
    }

    @Test
    fun `Generates the NestedFlat methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("evenMore")
            assertThat(parameters.first().type).isEqualTo(messageType("MoreDetail"))
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.nestedFlat($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setMainDetail($TEST_NAMESPACE.Detail.newBuilder()
                |      .setEvenMore(evenMore)
                |      .build()
                |    )
                |    .build())
                |}
            """.asNormalizedString()
            )
        }
    }

    @Test
    fun `Generates the NestedFlat methods with repeated fields`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("more_details"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("moreDetails")
            assertThat(parameters.first().type).isEqualTo(
                List::class.asTypeName().parameterizedBy(messageType("Detail"))
            )
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.nestedFlat($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .addAllMoreDetails(moreDetails)
                |    .build())
                |}
            """.asNormalizedString()
            )
        }
    }

    @Test
    fun `Generates the NestedFlat methods with repeated nested fields`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("more_details[0].even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstType().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("evenMore")
            assertThat(parameters.first().type).isEqualTo(messageType("MoreDetail"))
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.nestedFlat($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .addMoreDetails(0, $TEST_NAMESPACE.Detail.newBuilder()
                |      .setEvenMore(evenMore)
                |      .build()
                |    )
                |    .build())
                |}
            """.asNormalizedString()
            )
        }
    }

    @Test
    fun `Generates the NestedFlatPrimitive methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlatPrimitive",
                    flattenedMethods = listOf(
                        FlattenedMethod(listOf("main_detail.useful"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods =
            generate(opts).firstType().funSpecs.filter { it.name == "nestedFlatPrimitive" }
        assertThat(methods).hasSize(1)
        assertThat(methods.map { it.returnType }).containsExactly(futureCall("TestResponse"))

        val method = methods.find { it.parameters.size == 1 }
        assertThat(method).isNotNull()
        method?.apply {
            assertThat(parameters.first().name).isEqualTo("useful")
            assertThat(parameters.first().type).isEqualTo(Boolean::class.asTypeName())
            assertThat(this.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |  it.nestedFlatPrimitive($TEST_NAMESPACE.TestRequest.newBuilder()
                |    .setMainDetail($TEST_NAMESPACE.Detail.newBuilder()
                |      .setUseful(useful)
                |      .build()
                |    )
                |    .build())
                |}
            """.asNormalizedString()
            )
        }
    }
}