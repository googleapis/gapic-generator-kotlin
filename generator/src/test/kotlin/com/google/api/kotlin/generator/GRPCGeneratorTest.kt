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
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.firstSourceType
import com.google.api.kotlin.props
import com.google.api.kotlin.sources
import com.google.api.kotlin.stream
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

internal class GRPCGeneratorTest : BaseGeneratorTest() {

    @Test
    fun `Generates with class documentation`() {
        val opts = ServiceOptions(listOf())

        assertThat(generate(opts).firstSourceType().kdoc.toString()).isNotEmpty()
    }

    @Test
    fun `Generates with prepare`() {
        val opts = ServiceOptions(listOf())

        val methods = generate(opts).firstSourceType().funSpecs

        val method = methods.first { it.name == "prepare" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Prepare for an API call by setting any desired options. For example:
            |*
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val response = client.prepare {
            |*     withMetadata("my-custom-header", listOf("some", "thing"))
            |* }.test(request).get()
            |* ```
            |*
            |* You may save the client returned by this call and reuse it if you
            |* plan to make multiple requests with the same settings.
            |*/
            |fun prepare(
            |    init: com.google.kgax.grpc.ClientCallOptions.Builder.() -> kotlin.Unit
            |): google.example.TheTest {
            |    val options = com.google.kgax.grpc.ClientCallOptions.Builder(options)
            |    options.init()
            |    return google.example.TheTest(channel, options.build())
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates with required static imports`() {
        val opts = ServiceOptions(listOf())

        val imports = generate(opts).sources().first().imports
        assertThat(imports).containsExactly(
            ClassName(GrpcTypes.Support.SUPPORT_LIB_PACKAGE, "pager"),
            ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "prepare")
        )
    }

    @Test
    fun `Generates the test method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "Test")))

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "test" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.test(
            |*     TestRequest {
            |*     }
            |*)
            |* ```
            |*
            |* @param request the request object for the API call
            |*/
            |fun test(
            |    request: google.example.TestRequest
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.test(request)
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the LRO test method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "OperationTest")))

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "operationTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.operationTest(
            |*     TestRequest {
            |*     }
            |*)
            |* ```
            |*
            |* @param request the request object for the API call
            |*/
            |fun operationTest(
            |    request: google.example.TestRequest
            |): com.google.kgax.grpc.LongRunningCall<google.example.TestResponse> = com.google.kgax.grpc.LongRunningCall<google.example.TestResponse>(
            |    stubs.operation,
            |    stubs.api.executeFuture { it.operationTest(request) },
            |    google.example.TestResponse::class.java
            |)
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamTest")))

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.returnType).isEqualTo(stream("TestRequest", "TestResponse"))
        assertThat(method.parameters).isEmpty()
        assertThat(method.body.asNormalizedString()).isEqualTo(
            """
            |return stubs.api.executeStreaming { it::streamTest }
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
                        FlattenedMethod(props("query")),
                        FlattenedMethod(props("query", "main_detail"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(2)

        val oneParamMethod = methods.first { it.parameters.size == 1 }
        assertThat(oneParamMethod.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.streamTest(
            |*     query
            |*)
            |* ```
            |*
            |* @param query
            |*/
            |fun streamTest(
            |    query: kotlin.String
            |): com.google.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.prepare {
            |        withInitialRequest(
            |            google.example.TestRequest {
            |                this.query = query
            |            }
            |        )
            |    }.executeStreaming { it::streamTest }
            |""".asNormalizedString()
        )

        val twoParamMethod = methods.first { it.parameters.size == 2 }
        assertThat(twoParamMethod.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.streamTest(
            |*     query,
            |*     Detail {
            |*         this.mainDetail = mainDetail
            |*     }
            |*)
            |* ```
            |*
            |* @param query
            |*
            |* @param mainDetail
            |*/
            |fun streamTest(
            |    query: kotlin.String,
            |    mainDetail: google.example.Detail
            |): com.google.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.prepare {
            |        withInitialRequest(
            |            google.example.TestRequest {
            |                this.query = query
            |                this.mainDetail = mainDetail
            |            }
            |        )
            }.executeStreaming { it::streamTest }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamClientTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamClientTest")))

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "streamClientTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.streamClientTest(
            |*     TestRequest {
            |*     }
            |*)
            |* ```
            |*/
            |fun streamClientTest(): com.google.kgax.grpc.ClientStreamingCall<google.example.TestRequest, google.example.TestResponse> = stubs.api.executeClientStreaming {
            |    it::streamClientTest
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamServerTest method`() {
        val opts = ServiceOptions(listOf(MethodOptions(name = "StreamServerTest")))

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.streamServerTest(
            |*     TestRequest {
            |*     }
            |*)
            |* ```
            |*/
            |fun streamServerTest(
            |    request: google.example.TestRequest
            |): com.google.kgax.grpc.ServerStreamingCall<google.example.TestResponse> = stubs.api.executeServerStreaming { stub, observer ->
            |    stub.streamServerTest(request, observer)
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamServerTest method with flattening`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "StreamServerTest",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.streamServerTest(
            |*     MoreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |*)
            |* ```
            |*/
            |fun streamServerTest(
            |    evenMore: google.example.MoreDetail
            |): com.google.kgax.grpc.ServerStreamingCall<google.example.TestResponse> = stubs.api.executeServerStreaming { stub, observer ->
            |    stub.streamServerTest(google.example.TestRequest {
            |        mainDetail = google.example.Detail {
            |            this.evenMore = evenMore
            |        }
            |    },
            |    observer)
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the testFlat methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "TestFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("query")),
                        FlattenedMethod(props("query", "main_detail"))
                    ),
                    keepOriginalMethod = true
                )
            )
        )

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "testFlat" }
        assertThat(methods).hasSize(3)

        val original =
            methods.first { it.parameters.size == 1 && it.parameters[0].name == "request" }
        assertThat(original.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.testFlat(
            |*     TestRequest {
            |*     }
            |*)
            |* ```
            |*
            |* @param request the request object for the API call
            |*/
            |fun testFlat(
            |    request: google.example.TestRequest
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.testFlat(request)
            |}
            |""".asNormalizedString()
        )

        val oneArg = methods.first { it.parameters.size == 1 && it.parameters[0].name != "request" }
        assertThat(oneArg.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.testFlat(
            |*     query
            |*)
            |* ```
            |*
            |* @param query
            |*/
            |fun testFlat(
            |    query: kotlin.String
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.testFlat(google.example.TestRequest {
            |        this.query = query
            |    })
            |}
            |""".asNormalizedString()
        )

        val twoArg = methods.first { it.parameters.size == 2 }
        assertThat(twoArg.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.testFlat(
            |*     query,
            |*     Detail {
            |*         this.mainDetail = mainDetail
            |*     }
            |*)
            |* ```
            |*
            |* @param query
            |*
            |* @param mainDetail
            |*/
            |fun testFlat(
            |    query: kotlin.String,
            |    mainDetail: google.example.Detail
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.testFlat(google.example.TestRequest {
            |        this.query = query
            |        this.mainDetail = mainDetail
            |    })
            |}""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the TestFlatWithoutOriginal methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "TestFlatWithoutOriginal",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods =
            generate(opts).firstSourceType().funSpecs.filter { it.name == "testFlatWithoutOriginal" }
        assertThat(methods).hasSize(1)

        val oneArg = methods.first { it.parameters.size == 1 }
        assertThat(oneArg.toString().asNormalizedString()).isEqualTo(
            """
                |/**
                |*
                |*
                |* For example:
                |* ```
                |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
                |* val result = client.testFlatWithoutOriginal(
                |*     Detail {
                |*         this.mainDetail = mainDetail
                |*     }
                |*)
                |* ```
                |*
                |* @param mainDetail
                |*/
                |fun testFlatWithoutOriginal(
                |    mainDetail: google.example.Detail
                |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
                |    it.testFlatWithoutOriginal(
                |        google.example.TestRequest {
                |            this.mainDetail = mainDetail
                |        }
                |    )
                |}
                |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the NestedFlat methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.nestedFlat(
            |*     MoreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |*)
            |* ```
            |*/
            |fun nestedFlat(
            |    evenMore: google.example.MoreDetail
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.nestedFlat(google.example.TestRequest {
            |        mainDetail = google.example.Detail {
            |            this.evenMore = evenMore
            |        }
            |    })
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the NestedFlat methods with repeated fields`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("more_details"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.nestedFlat(
            |*     Detail {
            |*         addAllMoreDetails(moreDetails)
            |*     }
            |*)
            |* ```
            |*
            |* @param moreDetails
            |*/
            |fun nestedFlat(
            |    moreDetails: kotlin.collections.List<google.example.Detail>
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.nestedFlat(
            |        google.example.TestRequest {
            |            addAllMoreDetails(moreDetails)
            |        }
            |    )
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the NestedFlat methods with repeated nested fields`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("more_details[0].even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).firstSourceType().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)

        val method = methods.first { it.parameters.size == 1 }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.nestedFlat(
            |*     MoreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |*)
            |* ```
            |*/
            |fun nestedFlat(
            |    evenMore: google.example.MoreDetail
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.nestedFlat(google.example.TestRequest {
            |        addMoreDetails(0, google.example.Detail {
            |            this.evenMore = evenMore
            |        }
            |    )})
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the NestedFlatPrimitive methods`() {
        val opts = ServiceOptions(
            listOf(
                MethodOptions(
                    name = "NestedFlatPrimitive",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail.useful"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods =
            generate(opts).firstSourceType().funSpecs.filter { it.name == "nestedFlatPrimitive" }
        assertThat(methods).hasSize(1)

        val method = methods.first() { it.parameters.size == 1 }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = google.example.TheTest.fromServiceAccount(YOUR_KEY_FILE)
            |* val result = client.nestedFlatPrimitive(
            |*     main_detail.useful
            |*)
            |* ```
            |*/
            |fun nestedFlatPrimitive(
            |    useful: kotlin.Boolean
            |): com.google.kgax.grpc.FutureCall<google.example.TestResponse> = stubs.api.executeFuture {
            |    it.nestedFlatPrimitive(google.example.TestRequest {
            |        mainDetail = google.example.Detail {
            |            this.useful = useful
            |        }
            |    })
            |}
            |""".asNormalizedString()
        )
    }
}
