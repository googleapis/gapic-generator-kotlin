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

import com.google.api.kotlin.BaseClientGeneratorTest
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.LongRunningResponse
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.config.asPropertyPath
import com.google.api.kotlin.props
import com.google.api.kotlin.sources
import com.google.api.kotlin.testServiceClient
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

internal class GRPCGeneratorTest : BaseClientGeneratorTest(GRPCGenerator()) {

    @Test
    fun `Generates with class documentation`() {
        val opts = ServiceOptions(methods = listOf())

        assertThat(generate(opts).testServiceClient().kdoc.toString()).isNotEmpty()
    }

    @Test
    fun `Generates with prepare`() {
        val opts = ServiceOptions(methods = listOf())

        val methods = generate(opts).testServiceClient().funSpecs

        val method = methods.first { it.name == "prepare" }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* Prepare for an API call by setting any desired options. For example:
            |*
            |* ```
            |* val client = TheTest.create()
            |* val response = client.prepare {
            |*     withMetadata("my-custom-header", listOf("some", "thing"))
            |* }.test(request)
            |* ```
            |*
            |* You may save the client returned by this call and reuse it if you
            |* plan to make multiple requests with the same settings.
            |*/
            |fun prepare(
            |    init: com.google.api.kgax.grpc.ClientCallOptions.Builder.() -> kotlin.Unit
            |): google.example.TheTest {
            |    val optionsBuilder = com.google.api.kgax.grpc.ClientCallOptions.Builder(options)
            |    optionsBuilder.init()
            |    return google.example.TheTest(channel, optionsBuilder.build())
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates with required static imports`() {
        val opts = ServiceOptions(methods = listOf())

        val imports = generate(opts).sources().first().imports
        assertThat(imports).containsExactly(
            ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "pager"),
            ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, "prepare"),
            ClassName("kotlinx.coroutines", "coroutineScope"),
            ClassName("kotlinx.coroutines", "async")
        )
    }

    @Test
    fun `Generates the test method`() {
        val opts = ServiceOptions(methods = listOf(MethodOptions(name = "Test")))

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "test" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |* This is the test method
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.test(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*
            |* @param request the request object for the API call
            |*/
            |suspend fun test(
            |    request: google.example.TestRequest
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("test") {
            |    it.test(request)
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the LRO method`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "OperationTest",
                    longRunningResponse = LongRunningResponse(
                        responseType = ".google.example.SomeResponse",
                        metadataType = ".google.example.SomeMetadata"
                    )
                )
            )
        )

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "operationTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.operationTest(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*
            |* @param request the request object for the API call
            |*/
            |suspend fun operationTest(
            |    request: google.example.TestRequest
            |): com.google.api.kgax.grpc.LongRunningCall<google.example.SomeResponse> = coroutineScope {
            |    com.google.api.kgax.grpc.LongRunningCall<google.example.SomeResponse>(
            |        stubs.operation,
            |        async { stubs.api.execute("operationTest") { it.operationTest(request) } },
            |        google.example.SomeResponse::class.java
            |    )
            |}
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamTest method`() {
        val opts = ServiceOptions(methods = listOf(MethodOptions(name = "StreamTest")))

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TheTest.create()
            | * val result = client.streamTest(
            | *     testRequest {
            | *     }
            | * )
            | * ```
            | */
            |suspend fun streamTest(): com.google.api.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |   stubs.api.executeStreaming("streamTest") { it::streamTest }
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamTest method with flattening`() {
        val opts = ServiceOptions(
            methods = listOf(
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

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "streamTest" }
        assertThat(methods).hasSize(2)

        val oneParamMethod = methods.first { it.parameters.size == 1 }
        assertThat(oneParamMethod.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.streamTest(
            |*     query
            |* )
            |* ```
            |*
            |* @param query the query
            |*/
            |suspend fun streamTest(
            |    query: kotlin.String
            |): com.google.api.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.prepare {
            |        withInitialRequest(
            |            google.example.testRequest {
            |                this.query = query
            |            }
            |        )
            |    }.executeStreaming("streamTest") { it::streamTest }
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
            |* val client = TheTest.create()
            |* val result = client.streamTest(
            |*     query,
            |*     detail {
            |*         this.mainDetail = mainDetail
            |*     }
            |* )
            |* ```
            |*
            |* @param query the query
            |*
            |* @param mainDetail
            |*/
            |suspend fun streamTest(
            |    query: kotlin.String,
            |    mainDetail: google.example.Detail
            |): com.google.api.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.prepare {
            |        withInitialRequest(
            |            google.example.testRequest {
            |                this.query = query
            |                this.mainDetail = mainDetail
            |            }
            |        )
            }.executeStreaming("streamTest") { it::streamTest }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamClientTest method`() {
        val opts = ServiceOptions(methods = listOf(MethodOptions(name = "StreamClientTest")))

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "streamClientTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.streamClientTest(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*/
            |suspend fun streamClientTest(): com.google.api.kgax.grpc.ClientStreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.executeClientStreaming("streamClientTest") {
            |        it::streamClientTest
            |    }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamClientTest method with flattening`() {
        val opts = ServiceOptions(methods = listOf(MethodOptions(name = "StreamClientTest")))

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "streamClientTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.streamClientTest(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*/
            |suspend fun streamClientTest(): com.google.api.kgax.grpc.ClientStreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.executeClientStreaming("streamClientTest") {
            |        it::streamClientTest
            |    }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamServerTest method`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "StreamServerTest",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.streamServerTest(
            |*     moreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |*)
            |* ```
            |*/
            |suspend fun streamServerTest(
            |    evenMore: google.example.MoreDetail
            |): com.google.api.kgax.grpc.ServerStreamingCall<google.example.TestResponse> =
            |    stubs.api.executeServerStreaming("streamServerTest") { stub, observer ->
            |        stub.streamServerTest(
            |            google.example.testRequest {
            |                mainDetail = google.example.detail {
            |                    this.evenMore = evenMore
            |                }
            |            },
            |            observer
            |        )
            |    }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the streamServerTest method with flattening`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "StreamServerTest",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "streamServerTest" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.streamServerTest(
            |*     moreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |* )
            |* ```
            |*/
            |suspend fun streamServerTest(
            |    evenMore: google.example.MoreDetail
            |): com.google.api.kgax.grpc.ServerStreamingCall<google.example.TestResponse> = stubs.api.executeServerStreaming("streamServerTest") { stub, observer ->
            |    stub.streamServerTest(google.example.testRequest {
            |        mainDetail = google.example.detail {
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
            methods = listOf(
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

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "testFlat" }
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
            |* val client = TheTest.create()
            |* val result = client.testFlat(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*
            |* @param request the request object for the API call
            |*/
            |suspend fun testFlat(
            |    request: google.example.TestRequest
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("testFlat") {
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
            |* val client = TheTest.create()
            |* val result = client.testFlat(
            |*     query
            |* )
            |* ```
            |*
            |* @param query the query
            |*/
            |suspend fun testFlat(
            |    query: kotlin.String
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("testFlat") {
            |    it.testFlat(google.example.testRequest {
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
            |* val client = TheTest.create()
            |* val result = client.testFlat(
            |*     query,
            |*     detail {
            |*         this.mainDetail = mainDetail
            |*     }
            |* )
            |* ```
            |*
            |* @param query the query
            |*
            |* @param mainDetail
            |*/
            |suspend fun testFlat(
            |    query: kotlin.String,
            |    mainDetail: google.example.Detail
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("testFlat") {
            |    it.testFlat(google.example.testRequest {
            |        this.query = query
            |        this.mainDetail = mainDetail
            |    })
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the TestFlatWithoutOriginal methods`() {
        val opts = ServiceOptions(
            methods = listOf(
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
            generate(opts).testServiceClient().funSpecs.filter { it.name == "testFlatWithoutOriginal" }
        assertThat(methods).hasSize(1)

        val oneArg = methods.first { it.parameters.size == 1 }
        assertThat(oneArg.toString().asNormalizedString()).isEqualTo(
            """
                |/**
                |*
                |*
                |* For example:
                |* ```
                |* val client = TheTest.create()
                |* val result = client.testFlatWithoutOriginal(
                |*     detail {
                |*         this.mainDetail = mainDetail
                |*     }
                |* )
                |* ```
                |*
                |* @param mainDetail
                |*/
                |suspend fun testFlatWithoutOriginal(
                |    mainDetail: google.example.Detail
                |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("testFlatWithoutOriginal") {
                |    it.testFlatWithoutOriginal(
                |        google.example.testRequest {
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
            methods = listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("main_detail.even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.nestedFlat(
            |*     moreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |* )
            |* ```
            |*/
            |suspend fun nestedFlat(
            |    evenMore: google.example.MoreDetail
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("nestedFlat") {
            |    it.nestedFlat(google.example.testRequest {
            |        mainDetail = google.example.detail {
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
            methods = listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("more_details"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.nestedFlat(
            |*     detail {
            |*         this.moreDetails = moreDetails
            |*     }
            |* )
            |* ```
            |*
            |* @param moreDetails
            |*/
            |suspend fun nestedFlat(
            |    moreDetails: kotlin.collections.List<google.example.Detail>
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("nestedFlat") {
            |    it.nestedFlat(
            |        google.example.testRequest {
            |            this.moreDetails = moreDetails
            |        }
            |    )
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the NestedFlat methods with repeated nested fields`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "NestedFlat",
                    flattenedMethods = listOf(
                        FlattenedMethod(props("more_details[0].even_more"))
                    ),
                    keepOriginalMethod = false
                )
            )
        )

        val methods = generate(opts).testServiceClient().funSpecs.filter { it.name == "nestedFlat" }
        assertThat(methods).hasSize(1)

        assertThat(methods.first().toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TheTest.create()
            | * val result = client.nestedFlat(
            | *     moreDetail {
            | *         this.evenMore = evenMore
            | *     }
            | *)
            | * ```
            | */
            |suspend fun nestedFlat(
            |    evenMore: google.example.MoreDetail
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("nestedFlat") {
            |    it.nestedFlat(google.example.testRequest {
            |        moreDetails(google.example.detail {
            |            this.evenMore = evenMore
            |    })})
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the NestedFlatPrimitive methods`() {
        val opts = ServiceOptions(
            methods = listOf(
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
            generate(opts).testServiceClient().funSpecs.filter { it.name == "nestedFlatPrimitive" }
        assertThat(methods).hasSize(1)

        val method = methods.first { it.parameters.size == 1 }
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            |*
            |*
            |* For example:
            |* ```
            |* val client = TheTest.create()
            |* val result = client.nestedFlatPrimitive(
            |*     mainDetail.useful
            |* )
            |* ```
            |*/
            |suspend fun nestedFlatPrimitive(
            |    useful: kotlin.Boolean
            |): com.google.api.kgax.grpc.CallResult<google.example.TestResponse> = stubs.api.execute("nestedFlatPrimitive") {
            |    it.nestedFlatPrimitive(google.example.testRequest {
            |        mainDetail = google.example.detail {
            |            this.useful = useful
            |        }
            |    })
            |}
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the PagedTest methods`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "PagedTest",
                    pagedResponse = PagedResponse(
                        pageSize = "page_size",
                        responseList = "responses",
                        requestPageToken = "page_token",
                        responsePageToken = "next_page_token"
                    )
                )
            )
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == "pagedTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TheTest.create()
            | * val pager = client.pagedTest(
            | *     pagedRequest {
            | *     }
            | *)
            | * val page = pager.next()
            | * ```
            | *
            | * @param request the request object for the API call
            | */
            |suspend fun pagedTest(
            |   request: google.example.PagedRequest
            |): kotlinx.coroutines.channels.ReceiveChannel<com.google.api.kgax.grpc.PageWithMetadata<kotlin.Int>> =
            |    pager(
            |        method = {
            |            request -> stubs.api.execute("pagedTest") {
            |                it.pagedTest(request)
            |            }
            |        },
            |        initialRequest = { request },
            |        nextRequest = { request, token -> request.toBuilder().setPageToken(token).build() },
            |        nextPage = { response: com.google.api.kgax.grpc.CallResult<google.example.PagedResponse> ->
            |            com.google.api.kgax.grpc.PageWithMetadata<kotlin.Int>(response.body.responsesList, response.body.nextPageToken, response.metadata)
            |        }
            |    )
            |""".asNormalizedString()
        )
    }

    @Test
    fun `Generates the PagedTest methods with flattened page parameter`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "PagedTest",
                    pagedResponse = PagedResponse(
                        pageSize = "page_size",
                        responseList = "responses",
                        requestPageToken = "page_token",
                        responsePageToken = "next_page_token"
                    ),
                    keepOriginalMethod = false,
                    flattenedMethods = listOf(
                        FlattenedMethod(
                            listOf(
                                "flag".asPropertyPath(),
                                "page_size".asPropertyPath()
                            )
                        )
                    )
                )
            )
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == "pagedTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TheTest.create()
            | * val pager = client.pagedTest(
            | *     flag,
            | *     pageSize
            | *)
            | * val page = pager.next()
            | * ```
            | *
            | * @param flag
            | *
            | * @param pageSize
            | */
            |suspend fun pagedTest(
            |     flag: kotlin.Boolean,
            |     pageSize: kotlin.Int
            |): kotlinx.coroutines.channels.ReceiveChannel<com.google.api.kgax.grpc.PageWithMetadata<kotlin.Int>> = pager(
            |    method = { request ->
            |        stubs.api.execute("pagedTest") { it.pagedTest(request) }
            |    },
            |    initialRequest = {
            |        google.example.pagedRequest {
            |            this.flag = flag
            |            this.pageSize = pageSize
            |        }
            |    },
            |    nextRequest = { request, token -> request.toBuilder().setPageToken(token).build() },
            |    nextPage = { response: com.google.api.kgax.grpc.CallResult<google.example.PagedResponse> ->
            |        com.google.api.kgax.grpc.PageWithMetadata<kotlin.Int>(response.body.responsesList, response.body.nextPageToken, response.metadata)
            |    }
            |)
            |""".trimIndent().asNormalizedString()
        )
    }

    @Test
    fun `Generates the PagedTest methods without paging`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "PagedTest"
                )
            )
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == "pagedTest" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TheTest.create()
            | * val result = client.pagedTest(
            | *     pagedRequest {
            | *     }
            | *)
            | * ```
            | *
            | * @param request the request object for the API call
            | */
            |suspend fun pagedTest(request: google.example.PagedRequest): com.google.api.kgax.grpc.CallResult<google.example.PagedResponse> =
            |    stubs.api.execute("pagedTest") { it.pagedTest(request) }
            |""".asNormalizedString()
        )
    }
}
