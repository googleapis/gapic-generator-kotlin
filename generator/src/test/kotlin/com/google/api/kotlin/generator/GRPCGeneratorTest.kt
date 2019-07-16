/*
 * Copyright 2019 Google LLC
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
import com.google.api.kotlin.ClientPluginOptions
import com.google.api.kotlin.GeneratedArtifact
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.LongRunningResponse
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.config.asPropertyPath
import com.google.api.kotlin.props
import com.google.api.kotlin.sources
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test

/**
 * Tests for the [GRPCGenerator] using the primary test proto
 * `proto/google/example/test.proto` in the test resources directory.
 */
internal class GRPCGeneratorTest : BaseClientGeneratorTest("test", "TestServiceClient") {

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
            |* val client = TestServiceClient.create()
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
            |): google.example.TestServiceClient {
            |    val optionsBuilder = com.google.api.kgax.grpc.ClientCallOptions.Builder(options)
            |    optionsBuilder.init()
            |    return google.example.TestServiceClient(channel, optionsBuilder.build())
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
            |* val client = TestServiceClient.create()
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
            |): google.example.TestResponse = stubs.api.execute(context = "test") {
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
            |* val client = TestServiceClient.create()
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
            |        async { stubs.api.execute(context = "operationTest") { it.operationTest(request) } },
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
            | * val client = TestServiceClient.create()
            | * val result = client.streamTest(
            | *     testRequest {
            | *     }
            | * )
            | * ```
            | */
            |fun streamTest(): com.google.api.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |   stubs.api.executeStreaming(context = "streamTest") { it::streamTest }
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
            |* val client = TestServiceClient.create()
            |* val result = client.streamTest(
            |*     query
            |* )
            |* ```
            |*
            |* @param query the query
            |*/
            |fun streamTest(
            |    query: kotlin.String
            |): com.google.api.kgax.grpc.StreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.prepare {
            |        withInitialRequest(
            |            google.example.testRequest {
            |                this.query = query
            |            }
            |        )
            |    }.executeStreaming(context = "streamTest") { it::streamTest }
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
            |* val client = TestServiceClient.create()
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
            |fun streamTest(
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
            }.executeStreaming(context = "streamTest") { it::streamTest }
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
            |* val client = TestServiceClient.create()
            |* val result = client.streamClientTest(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*/
            |fun streamClientTest(): com.google.api.kgax.grpc.ClientStreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.executeClientStreaming(context = "streamClientTest") {
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
            |* val client = TestServiceClient.create()
            |* val result = client.streamClientTest(
            |*     testRequest {
            |*     }
            |* )
            |* ```
            |*/
            |fun streamClientTest(): com.google.api.kgax.grpc.ClientStreamingCall<google.example.TestRequest, google.example.TestResponse> =
            |    stubs.api.executeClientStreaming(context = "streamClientTest") {
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
            |* val client = TestServiceClient.create()
            |* val result = client.streamServerTest(
            |*     moreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |*)
            |* ```
            |*/
            |fun streamServerTest(
            |    evenMore: google.example.MoreDetail
            |): com.google.api.kgax.grpc.ServerStreamingCall<google.example.TestResponse> =
            |    stubs.api.executeServerStreaming(context = "streamServerTest") { stub, observer ->
            |        stub.streamServerTest(
            |            google.example.testRequest {
            |                this.mainDetail = google.example.detail {
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
            |* val client = TestServiceClient.create()
            |* val result = client.streamServerTest(
            |*     moreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |* )
            |* ```
            |*/
            |fun streamServerTest(
            |    evenMore: google.example.MoreDetail
            |): com.google.api.kgax.grpc.ServerStreamingCall<google.example.TestResponse> = stubs.api.executeServerStreaming(context = "streamServerTest") { stub, observer ->
            |    stub.streamServerTest(google.example.testRequest {
            |        this.mainDetail = google.example.detail {
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
            |* val client = TestServiceClient.create()
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
            |): google.example.TestResponse = stubs.api.execute(context = "testFlat") {
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
            |* val client = TestServiceClient.create()
            |* val result = client.testFlat(
            |*     query
            |* )
            |* ```
            |*
            |* @param query the query
            |*/
            |suspend fun testFlat(
            |    query: kotlin.String
            |): google.example.TestResponse = stubs.api.execute(context = "testFlat") {
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
            |* val client = TestServiceClient.create()
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
            |): google.example.TestResponse = stubs.api.execute(context = "testFlat") {
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
                |* val client = TestServiceClient.create()
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
                |): google.example.TestResponse = stubs.api.execute(context = "testFlatWithoutOriginal") {
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
            |* val client = TestServiceClient.create()
            |* val result = client.nestedFlat(
            |*     moreDetail {
            |*         this.evenMore = evenMore
            |*     }
            |* )
            |* ```
            |*/
            |suspend fun nestedFlat(
            |    evenMore: google.example.MoreDetail
            |): google.example.TestResponse = stubs.api.execute(context = "nestedFlat") {
            |    it.nestedFlat(google.example.testRequest {
            |        this.mainDetail = google.example.detail {
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
            |* val client = TestServiceClient.create()
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
            |): google.example.TestResponse = stubs.api.execute(context = "nestedFlat") {
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
            | * val client = TestServiceClient.create()
            | * val result = client.nestedFlat(
            | *     moreDetail {
            | *         this.evenMore = evenMore
            | *     }
            | *)
            | * ```
            | */
            |suspend fun nestedFlat(
            |    evenMore: google.example.MoreDetail
            |): google.example.TestResponse = stubs.api.execute(context = "nestedFlat") {
            |    it.nestedFlat(google.example.testRequest {
            |        this.moreDetails(google.example.detail {
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
            |* val client = TestServiceClient.create()
            |* val result = client.nestedFlatPrimitive(
            |*     mainDetail.useful
            |* )
            |* ```
            |*/
            |suspend fun nestedFlatPrimitive(
            |    useful: kotlin.Boolean
            |): google.example.TestResponse = stubs.api.execute(context = "nestedFlatPrimitive") {
            |    it.nestedFlatPrimitive(google.example.testRequest {
            |        this.mainDetail = google.example.detail {
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
            | * val client = TestServiceClient.create()
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
            |): kotlinx.coroutines.channels.ReceiveChannel<com.google.api.kgax.Page<kotlin.Int>> =
            |    pager(
            |        method = {
            |            request -> stubs.api.execute(context = "pagedTest") {
            |                it.pagedTest(request)
            |            }
            |        },
            |        initialRequest = { request },
            |        nextRequest = { request, token -> request.toBuilder().setPageToken(token).build() },
            |        nextPage = { response: google.example.PagedResponse ->
            |            com.google.api.kgax.Page<kotlin.Int>(response.responsesList, response.nextPageToken)
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
            | * val client = TestServiceClient.create()
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
            |): kotlinx.coroutines.channels.ReceiveChannel<com.google.api.kgax.Page<kotlin.Int>> = pager(
            |    method = { request ->
            |        stubs.api.execute(context = "pagedTest") { it.pagedTest(request) }
            |    },
            |    initialRequest = {
            |        google.example.pagedRequest {
            |            this.flag = flag
            |            this.pageSize = pageSize
            |        }
            |    },
            |    nextRequest = { request, token -> request.toBuilder().setPageToken(token).build() },
            |    nextPage = { response: google.example.PagedResponse ->
            |        com.google.api.kgax.Page<kotlin.Int>(response.responsesList, response.nextPageToken)
            |    }
            |)
            |""".trimIndent().asNormalizedString()
        )
    }

    @Test
    fun `Generates the PagedTest methods without paging`() {
        val opts = ServiceOptions(
            methods = listOf(MethodOptions(name = "PagedTest"))
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
            | * val client = TestServiceClient.create()
            | * val result = client.pagedTest(
            | *     pagedRequest {
            | *     }
            | *)
            | * ```
            | *
            | * @param request the request object for the API call
            | */
            |suspend fun pagedTest(request: google.example.PagedRequest): google.example.PagedResponse =
            |    stubs.api.execute(context = "pagedTest") { it.pagedTest(request) }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `skips the badly paged NotPagedTest method`() = skipsBadlyPagedMethod("NotPagedTest")

    @Test
    fun `skips the badly paged StillNotPagedTest method`() =
        skipsBadlyPagedMethod("StillNotPagedTest")

    @Test
    fun `skips the badly paged NotPagedTest2 method`() = skipsBadlyPagedMethod("NotPagedTest2")

    private fun skipsBadlyPagedMethod(methodName: String) {
        val opts = ServiceOptions(
            methods = listOf(MethodOptions(name = methodName))
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == methodName }
        assertThat(methods).isEmpty()
    }

    @Test
    fun `generates an empty method`() {
        val opts = ServiceOptions(
            methods = listOf(MethodOptions(name = "Empty"))
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == "empty" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TestServiceClient.create()
            | * val result = client.empty(
            | *     testRequest {
            | *     }
            | *)
            | * ```
            | *
            | * @param request the request object for the API call
            | */
            |suspend fun empty(request: google.example.TestRequest) {
            |    stubs.api.execute(context = "empty") { it.empty(request) }
            |}""".asNormalizedString()
        )
    }

    @Test
    fun `generates another empty method`() {
        val opts = ServiceOptions(
            methods = listOf(MethodOptions(name = "StillEmpty"))
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == "stillEmpty" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TestServiceClient.create()
            | * val result = client.stillEmpty()
            | * ```
            | *
            | * @param request the request object for the API call
            | */
            |suspend fun stillEmpty(): google.example.TestResponse =
            |    stubs.api.execute(context = "stillEmpty") { it.stillEmpty(com.google.protobuf.Empty.getDefaultInstance()) }
            |""".asNormalizedString()
        )
    }

    @Test
    fun `generates a really empty method`() {
        val opts = ServiceOptions(
            methods = listOf(MethodOptions(name = "ReallyEmpty"))
        )

        val methods =
            generate(opts).testServiceClient().funSpecs.filter { it.name == "reallyEmpty" }
        assertThat(methods).hasSize(1)

        val method = methods.first()
        assertThat(method.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = TestServiceClient.create()
            | * val result = client.reallyEmpty()
            | * ```
            | *
            | * @param request the request object for the API call
            | */
            |suspend fun reallyEmpty() {
            |    stubs.api.execute(context = "reallyEmpty") { it.reallyEmpty(com.google.protobuf.Empty.getDefaultInstance()) }
            |}""".asNormalizedString()
        )
    }
}

// Additional tests for non-standard naming patterns
internal class GRPCGeneratorNameTest : BaseClientGeneratorTest(
    protoFileName = "test_names",
    clientClassName = "SomeServiceClient",
    protoDirectory = "names",
    namespace = "names"
) {

    @Test
    fun `generates the GetUser method`() {
        val opts = ServiceOptions(
            methods = listOf(
                MethodOptions(
                    name = "GetUser",
                    keepOriginalMethod = false,
                    flattenedMethods = listOf(
                        FlattenedMethod(
                            listOf(
                                "_user.n_a_m_e".asPropertyPath()
                            )
                        ),
                        FlattenedMethod(
                            listOf(
                                "an_int".asPropertyPath(),
                                "aString".asPropertyPath(),
                                "a_bool".asPropertyPath(),
                                "_user".asPropertyPath()
                            )
                        )
                    )
                )
            )
        )

        val methods =
            generate(opts).someServiceClient().funSpecs.filter { it.name == "getUser" }
        assertThat(methods).hasSize(2)

        val firstMethod = methods.find { it.parameters.size == 1 }
        assertThat(firstMethod.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = SomeServiceClient.create()
            | * val result = client.getUser(
            | *     user.name
            | *)
            | * ```
            | *
            | * @param name
            | */
            |suspend fun getUser(name: kotlin.String): names.User = stubs.api.execute(context = "getUser") { 
            |    it.getUser(names.thing {
            |        this.user = names.user { this.name = name }
            |    })
            |}
            """.asNormalizedString()
        )

        val secondMethod = methods.find { it.parameters.size == 4 }
        assertThat(secondMethod.toString().asNormalizedString()).isEqualTo(
            """
            |/**
            | *
            | *
            | * For example:
            | * ```
            | * val client = SomeServiceClient.create()
            | * val result = client.getUser(
            | *     anInt,
            | *     aString,
            | *     aBool,
            | *     user {
            | *         this.user = user
            | *     }
            | *)
            | * ```
            | *
            | * @param anInt
            | *
            | * @param aString
            | *
            | * @param aBool
            | *
            | * @param user
            |*/
            |suspend fun getUser(anInt: kotlin.Int, aString: kotlin.String, aBool: kotlin.Boolean, user: names.User): names.User = 
            |    stubs.api.execute(context = "getUser") { 
            |        it.getUser(names.thing {
            |            this.anInt = anInt
            |            this.aString = aString
            |            this.aBool = aBool
            |            this.user = user
            |        })
            |    }
            """.asNormalizedString()
        )
    }
}

// The lite/normal code is almost identical.
// This base class is used to isolate the difference.
internal abstract class StubsImplTestContent(
    invocationOptions: ClientPluginOptions,
    private val marshallerClassName: String
) : BaseClientGeneratorTest("test", "TestServiceClient", invocationOptions = invocationOptions) {
    private val opts = ServiceOptions(methods = listOf(MethodOptions(name = "Test")))

    @Test
    fun `is annotated`() {
        val stub = generate(opts).testServiceClientStub()

        assertThat(stub.annotationSpecs.first().toString()).isEqualTo(
            "@javax.annotation.Generated(\"com.google.api.kotlin.generator.GRPCGenerator\")"
        )
    }

    @Test
    fun `has a unary testDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "testDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val testDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "Test"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a unary test method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "test" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun test(
            |    request: google.example.TestRequest
            |): com.google.common.util.concurrent.ListenableFuture<google.example.TestResponse> =
            |    futureUnaryCall(channel.newCall(testDescriptor, callOptions), request)
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamTestDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "streamTestDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val streamTestDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "StreamTest"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()
            |        ))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()
            |        ))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamTest method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "streamTest" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun streamTest(
            |    responseObserver: io.grpc.stub.StreamObserver<google.example.TestResponse>
            |): io.grpc.stub.StreamObserver<google.example.TestRequest> =
            |    asyncBidiStreamingCall(channel.newCall(streamTestDescriptor, callOptions), responseObserver)
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamClientTestDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "streamClientTestDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val streamClientTestDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.CLIENT_STREAMING)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "StreamClientTest"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()
            |        ))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()
            |        ))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamClientTest method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "streamClientTest" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun streamClientTest(
            |    responseObserver: io.grpc.stub.StreamObserver<google.example.TestResponse>
            |): io.grpc.stub.StreamObserver<google.example.TestRequest> =
            |    asyncClientStreamingCall(channel.newCall(streamClientTestDescriptor, callOptions), responseObserver)
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamServerTestDescriptor`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.propertySpecs.first { it.name == "streamServerTestDescriptor" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |private val streamServerTestDescriptor: io.grpc.MethodDescriptor<google.example.TestRequest, google.example.TestResponse> by lazy {
            |    io.grpc.MethodDescriptor.newBuilder<google.example.TestRequest, google.example.TestResponse>()
            |        .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
            |        .setFullMethodName(generateFullMethodName("google.example.TestService", "StreamServerTest"))
            |        .setSampledToLocalTracing(true)
            |        .setRequestMarshaller($marshallerClassName.marshaller(
            |            google.example.TestRequest.getDefaultInstance()
            |        ))
            |        .setResponseMarshaller($marshallerClassName.marshaller(
            |            google.example.TestResponse.getDefaultInstance()
            |        ))
            |        .build()
            |    }
            """.asNormalizedString()
        )
    }

    @Test
    fun `has a streaming streamServerTest method`() {
        val stub = generate(opts).testServiceClientStub()

        val descriptor = stub.funSpecs.first { it.name == "streamServerTest" }
        assertThat(descriptor.toString().asNormalizedString()).isEqualTo(
            """
            |fun streamServerTest(
            |    request: google.example.TestRequest,
            |    responseObserver: io.grpc.stub.StreamObserver<google.example.TestResponse>
            |) = asyncServerStreamingCall(channel.newCall(streamServerTestDescriptor, callOptions), request, responseObserver)
            """.asNormalizedString()
        )
    }
}

internal class FullStubsImplTest :
    StubsImplTestContent(ClientPluginOptions(), "io.grpc.protobuf.ProtoUtils")

internal class LiteStubsImplTest :
    StubsImplTestContent(ClientPluginOptions(lite = true), "io.grpc.protobuf.lite.ProtoLiteUtils")

private fun List<GeneratedArtifact>.testServiceClient() = findSource("TestServiceClient")
private fun List<GeneratedArtifact>.testServiceClientStub() = findSource("TestServiceClientStub")
private fun List<GeneratedArtifact>.someServiceClient() = findSource("SomeServiceClient")

private fun List<GeneratedArtifact>.findSource(name: String) =
    this.sources().firstOrNull { it.name == name }?.types?.first()
        ?: throw RuntimeException("Could not find $name in candidates: ${this.sources().map { it.name }}")