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

package com.google.api.kotlin.generator.grpc

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.TestableFunSpec
import com.google.api.kotlin.asTestable
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.SampleMethod
import com.google.api.kotlin.generator.AbstractGenerator
import com.google.api.kotlin.generator.getMethodComments
import com.google.api.kotlin.generator.getParameterComments
import com.google.api.kotlin.generator.isLongRunningOperation
import com.google.api.kotlin.types.GrpcTypes
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.asTypeName
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

private const val PLACEHOLDER_KEYFILE = "< keyfile >"

/** Generate the API method functions for the client. */
internal interface Functions {
    fun generate(ctx: GeneratorContext): List<TestableFunSpec>

    companion object {
        const val FUN_PREPARE = "prepare"

        const val PARAM_REQUEST = "request"
    }
}

internal class FunctionsImpl(
    private val unitTest: UnitTest
) : AbstractGenerator(), Functions {

    override fun generate(ctx: GeneratorContext): List<TestableFunSpec> {
        // we'll use this in the example text
        val firstMethodName = ctx.service.methodList
            .firstOrNull()?.name?.decapitalize()

        // extra methods (not part of the target API)
        val extras = listOf(
            FunSpec.builder(Functions.FUN_PREPARE)
                .addKdoc(
                    """
                        |Prepare for an API call by setting any desired options. For example:
                        |
                        |```
                        |val client = %T.fromServiceAccount(%L)
                        |val response = client.prepare {
                        |  withMetadata("my-custom-header", listOf("some", "thing"))
                        |}.%N(request).get()
                        |```
                        |
                        |You may save the client returned by this call and reuse it if you
                        |plan to make multiple requests with the same settings.
                        |""".trimMargin(),
                    ctx.className,
                    PLACEHOLDER_KEYFILE, firstMethodName ?: "method"
                )
                .returns(ctx.className)
                .addParameter(
                    ParameterSpec.builder(
                        "init",
                        LambdaTypeName.get(
                            GrpcTypes.Support.ClientCallOptionsBuilder,
                            listOf(),
                            Unit::class.asTypeName()
                        )
                    )
                        .build()
                )
                .addStatement(
                    "val options = %T(%N)",
                    GrpcTypes.Support.ClientCallOptionsBuilder,
                    Properties.PROP_CALL_OPTS
                )
                .addStatement("options.init()")
                .addStatement(
                    "return %T(%N, options.build())",
                    ctx.className, Properties.PROP_CHANNEL
                )
                .build()
                .asTestable()
        )

        // API methods
        val apiMethods = ctx.service.methodList.flatMap { method ->
            log.debug { "processing proto method: ${method.name}" }

            val name = method.name.decapitalize()
            val options = ctx.metadata[ctx.service].methods
                .firstOrNull { it.name == method.name } ?: MethodOptions(method.name)
            when {
                method.hasClientStreaming() || method.hasServerStreaming() ->
                    createStreamingMethods(ctx, method, name, options)
                else -> createUnaryMethods(ctx, method, name, options)
            }
        }

        return extras + apiMethods
    }

    private fun createUnaryMethods(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        options: MethodOptions
    ): List<TestableFunSpec> {
        val methods = mutableListOf<TestableFunSpec>()

        // add flattened methods
        methods.addAll(options.flattenedMethods.map { flattenedMethod ->
            val (parameters, request) = getFlattenedParameters(ctx, method, flattenedMethod)
            createUnaryMethod(
                ctx, method, methodName,
                parameters = parameters,
                requestObject = request,
                flatteningConfig = flattenedMethod,
                paging = options.pagedResponse,
                samples = options.samples
            )
        })

        // add normal method
        if (options.keepOriginalMethod) {
            val parameters = listOf(
                ParameterInfo(
                    ParameterSpec.builder(
                        Functions.PARAM_REQUEST,
                        ctx.typeMap.getKotlinType(method.inputType)
                    ).build()
                )
            )
            methods.add(
                createUnaryMethod(
                    ctx, method, methodName,
                    parameters = parameters,
                    requestObject = CodeBlock.of(Functions.PARAM_REQUEST),
                    paging = options.pagedResponse,
                    samples = options.samples
                )
            )
        }

        return methods.toList()
    }

    private fun createUnaryMethod(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        parameters: List<ParameterInfo>,
        requestObject: CodeBlock,
        flatteningConfig: FlattenedMethod? = null,
        paging: PagedResponse? = null,
        samples: List<SampleMethod> = listOf()
    ): TestableFunSpec {
        val m = FunSpec.builder(methodName)
            .addParameters(parameters.map { it.spec })

        val extraParamDocs = mutableListOf<CodeBlock>()

        // add request object to documentation
        if (flatteningConfig == null) {
            extraParamDocs.add(
                CodeBlock.of(
                    "@param %L the request object for the API call",
                    Functions.PARAM_REQUEST
                )
            )
        }

        // build method body
        when {
            method.isLongRunningOperation() -> {
                val returnType =
                    GrpcTypes.Support.LongRunningCall(getLongRunningResponseType(ctx, method))
                val realResponseType = getLongRunningResponseType(ctx, method)

                m.returns(returnType)
                m.addCode(
                    """
                        |return %T(
                        |  %N.%N,
                        |  %N.%N.executeFuture {
                        |    it.%L(%L)
                        |  }, %T::class.java)
                        |""".trimMargin(),
                    returnType,
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_OPERATION,
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_FUTURE,
                    methodName, requestObject,
                    realResponseType
                )
            }
            paging != null -> {
                val inputType = ctx.typeMap.getKotlinType(method.inputType)
                val outputType = ctx.typeMap.getKotlinType(method.outputType)
                val responseListItemType = getResponseListElementType(ctx, method, paging)

                // getters and setters for setting the page sizes, etc.
                val pageSizeSetter = getSetterName(paging.pageSize)
                val pageTokenSetter = getSetterName(paging.requestPageToken)
                val nextPageTokenGetter = getAccessorName(paging.responsePageToken)
                val responseListGetter = getAccessorRepeatedName(paging.responseList)

                // extra doc for the pageSize param not in the proto
                extraParamDocs.add(CodeBlock.of("@param pageSize number of results to fetch in each page"))
                m.addParameter(
                    ParameterSpec.builder("pageSize", Int::class)
                        .defaultValue("20")
                        .build()
                )

                // build method body using a pager
                m.returns(
                    GrpcTypes.Support.Pager(
                        inputType,
                        GrpcTypes.Support.CallResult(outputType), responseListItemType
                    )
                )
                m.addCode(
                    """
                        |return pager {
                        |    method = { request ->
                        |        %N.%N.executeFuture {
                        |            it.%L(request.toBuilder().%L(pageSize).build())
                        |        }.get()
                        |    }
                        |    initialRequest = {
                        |        %L
                        |    }
                        |    nextRequest = { request, token ->
                        |        request.toBuilder().%L(token).build()
                        |    }
                        |    nextPage = { response ->
                        |        %T(response.body.%L, response.body.%L, response.metadata)
                        |    }
                        |}
                        |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_FUTURE,
                    methodName, pageSizeSetter,
                    requestObject,
                    pageTokenSetter,
                    GrpcTypes.Support.PageResult(responseListItemType),
                    responseListGetter, nextPageTokenGetter
                )
            }
            else -> {
                val originalReturnType = ctx.typeMap.getKotlinType(method.outputType)

                m.returns(GrpcTypes.Support.FutureCall(originalReturnType))
                m.addCode(
                    """
                        |return %N.%N.executeFuture {
                        |  it.%L(%L)
                        |}
                        |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_FUTURE,
                    methodName, requestObject
                )
            }
        }

        // add documentation
        m.addKdoc(
            createMethodDoc(
                ctx, method, samples, flatteningConfig, parameters, extraParamDocs
            )
        )

        // add unit test
        val test = unitTest.createUnaryMethodUnitTest(
            ctx, method, methodName, parameters, flatteningConfig, paging
        )

        return TestableFunSpec(m.build(), test)
    }

    private fun createStreamingMethods(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        options: MethodOptions
    ): List<TestableFunSpec> {
        val methods = mutableListOf<TestableFunSpec>()

        // input / output types
        val normalInputType = ctx.typeMap.getKotlinType(method.inputType)
        val normalOutputType = ctx.typeMap.getKotlinType(method.outputType)

        // add flattened methods
        methods.addAll(options.flattenedMethods.map { flattenedMethod ->
            val (parameters, request) = getFlattenedParameters(ctx, method, flattenedMethod)

            val flattened = FunSpec.builder(methodName)
            if (method.hasClientStreaming() && method.hasServerStreaming()) {
                flattened.addKdoc(
                    createMethodDoc(
                        ctx, method, options.samples, flattenedMethod, parameters
                    )
                )
                flattened.addParameters(parameters.map { it.spec })
                flattened.returns(
                    GrpcTypes.Support.StreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                flattened.addCode(
                    """
                        |val stream = %N.%N.executeStreaming { it::%N }
                        |stream.requests.send(%L)
                        |return stream
                        |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_STREAM, methodName,
                    request
                )
            } else if (method.hasClientStreaming()) { // client only
                flattened.addKdoc(
                    createMethodDoc(
                        ctx, method, options.samples, flattenedMethod
                    )
                )
                flattened.returns(
                    GrpcTypes.Support.ClientStreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                flattened.addCode(
                    "return %N.%N.executeClientStreaming { it::%N }",
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_STREAM, methodName
                )
            } else if (method.hasServerStreaming()) { // server only
                flattened.addKdoc(
                    createMethodDoc(
                        ctx, method, options.samples, flattenedMethod, parameters
                    )
                )
                flattened.addParameters(parameters.map { it.spec })
                flattened.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                flattened.addCode(
                    """
                        |return %N.%N.executeServerStreaming { stub, observer ->
                        |  stub.%N(%L, observer)
                        |}
                        |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_STREAM,
                    methodName, request
                )
            } else {
                throw IllegalArgumentException("Unknown streaming type (not client or server)!")
            }

            // generate test
            val test = unitTest.createStreamingMethodTest(
                ctx, method, methodName, parameters, flattenedMethod
            )

            TestableFunSpec(flattened.build(), test)
        })

        // unchanged method
        if (options.keepOriginalMethod) {
            val normal = FunSpec.builder(methodName)
                .addKdoc(createMethodDoc(ctx, method, options.samples))
            val parameters = mutableListOf<ParameterInfo>()

            if (method.hasClientStreaming() && method.hasServerStreaming()) {
                normal.returns(
                    GrpcTypes.Support.StreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                normal.addCode(
                    "return %N.%N.executeStreaming { it::%N }",
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_STREAM, methodName
                )
            } else if (method.hasClientStreaming()) { // client only
                normal.returns(
                    GrpcTypes.Support.ClientStreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                normal.addCode(
                    "return %N.%N.executeClientStreaming { it::%N }",
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_STREAM, methodName
                )
            } else if (method.hasServerStreaming()) { // server only
                val param = ParameterSpec.builder(Functions.PARAM_REQUEST, normalInputType).build()
                parameters.add(ParameterInfo(param))
                normal.addParameter(param)
                normal.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                normal.addCode(
                    """
                        |return %N.%N.executeServerStreaming { stub, observer ->
                        |  stub.%N(%N, observer)
                        |}
                        |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_STREAM,
                    methodName, Functions.PARAM_REQUEST
                )
            } else {
                throw IllegalArgumentException("Unknown streaming type (not client or server)!")
            }

            // generate test
            val test =
                unitTest.createStreamingMethodTest(
                    ctx, method, methodName, parameters.toList(), null
                )

            methods.add(TestableFunSpec(normal.build(), test))
        }

        return methods.toList()
    }

    // create method comments from proto comments
    private fun createMethodDoc(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        samples: List<SampleMethod>,
        flatteningConfig: FlattenedMethod? = null,
        parameters: List<ParameterInfo> = listOf(),
        extras: List<CodeBlock> = listOf()
    ): CodeBlock {
        val doc = CodeBlock.builder()

        // remove the spacing from proto files
        fun cleanupComment(text: String?) = text
            ?.replace("\\n\\s".toRegex(), "\n")
            ?.trim()

        // add proto comments
        val text = ctx.proto.getMethodComments(ctx.service, method)
        doc.add("%L\n", cleanupComment(text) ?: "")

        // add any samples
        samples.forEach {
            // doc.add(createMethodDocSample(ctx, method, methodName, it, flatteningConfig))
        }

        // add parameter comments
        val paramComments = flatteningConfig?.parameters?.mapIndexed { idx, fullPath ->
            val path = fullPath.split(".")
            val fieldInfo = getProtoFieldInfoForPath(
                ctx, path, ctx.typeMap.getProtoTypeDescriptor(method.inputType)
            )
            val comment = fieldInfo.file.getParameterComments(fieldInfo)
            Pair(parameters[idx].spec.name, cleanupComment(comment))
        }?.filter { it.second != null } ?: listOf()
        paramComments.forEach { doc.add("\n@param %L %L\n", it.first, it.second) }

        // add any extra comments at the bottom (only used for the pageSize currently)
        extras.forEach { doc.add("\n%L\n", it) }

        // put it all together
        return doc.build()
    }
}
