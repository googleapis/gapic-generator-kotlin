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
import com.google.api.kotlin.indent
import com.google.api.kotlin.types.GrpcTypes
import com.google.api.kotlin.types.ProtoTypes
import com.google.api.kotlin.types.isNotProtobufEmpty
import com.google.api.kotlin.types.isProtobufEmpty
import com.google.api.kotlin.util.FieldNamer
import com.google.api.kotlin.util.Flattening
import com.google.api.kotlin.util.Flattening.getFlattenedParameters
import com.google.api.kotlin.util.ParameterInfo
import com.google.api.kotlin.util.ResponseTypes.getLongRunningResponseType
import com.google.api.kotlin.util.ResponseTypes.getResponseListElementType
import com.google.api.kotlin.util.isLongRunningOperation
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

/** Generate the API method functions for the client. */
internal interface Functions {
    fun generate(context: GeneratorContext): List<TestableFunSpec>

    companion object {
        const val FUN_PREPARE = "prepare"
        const val PARAM_REQUEST = "request"
    }
}

internal class FunctionsImpl(
    private val documentation: Documentation,
    private val unitTest: UnitTest
) : Functions {

    override fun generate(context: GeneratorContext): List<TestableFunSpec> {
        // we'll use this in the example text
        val firstMethodName = context.service.methodList
            .firstOrNull()?.name?.decapitalize()

        // extra methods (not part of the target API)
        val extras = listOf(
            FunSpec.builder(Functions.FUN_PREPARE)
                .addKdoc(
                    """
                    |Prepare for an API call by setting any desired options. For example:
                    |
                    |```
                    |%L
                    |val response = client.prepare {
                    |    withMetadata("my-custom-header", listOf("some", "thing"))
                    |}.%N(request)
                    |```
                    |
                    |You may save the client returned by this call and reuse it if you
                    |plan to make multiple requests with the same settings.
                    |""".trimMargin(),
                    documentation.getClientInitializer(context),
                    firstMethodName ?: "method"
                )
                .returns(context.className)
                .addParameter(
                    ParameterSpec.builder(
                        "init",
                        LambdaTypeName.get(
                            GrpcTypes.Support.ClientCallOptionsBuilder,
                            listOf(),
                            Unit::class.asTypeName()
                        )
                    ).build()
                )
                .addStatement(
                    "val optionsBuilder = %T(%N)",
                    GrpcTypes.Support.ClientCallOptionsBuilder,
                    Properties.PROP_CALL_OPTS
                )
                .addStatement("optionsBuilder.init()")
                .addStatement(
                    "return %T(%N, optionsBuilder.build())",
                    context.className, Properties.PROP_CHANNEL
                )
                .build()
                .asTestable(),

            FunSpec.builder("shutdownChannel")
                .addKdoc("Shutdown the [channel] associated with this client.\n")
                .addParameter(
                    ParameterSpec.builder("waitForSeconds", Long::class.java.asTypeName())
                        .defaultValue("5")
                        .build()
                )
                .addStatement(
                    "%L.shutdown().awaitTermination(waitForSeconds, %T.SECONDS)",
                    Properties.PROP_CHANNEL, TimeUnit::class.java.asTypeName()
                )
                .build()
                .asTestable()
        )

        // API methods
        val apiMethods = context.service.methodList.flatMap { method ->
            log.debug { "processing proto method: ${method.name}" }

            val options = context.metadata[context.service].methods
                .firstOrNull { it.name == method.name } ?: MethodOptions(method.name)
            when {
                method.hasClientStreaming() || method.hasServerStreaming() ->
                    createStreamingMethods(context, method, options)
                else -> createUnaryMethods(context, method, options)
            }
        }

        return extras + apiMethods
    }

    private fun createUnaryMethods(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        options: MethodOptions
    ): List<TestableFunSpec> {
        val methods = mutableListOf<TestableFunSpec>()

        // add flattened methods
        methods.addAll(options.flattenedMethods.map { flattenedMethod ->
            val (parameters, request) = Flattening.getFlattenedParameters(
                context,
                method,
                flattenedMethod
            )
            createUnaryMethod(
                context = context,
                method = method,
                methodOptions = options,
                parameters = parameters,
                requestObject = request,
                flattenedMethod = flattenedMethod
            )
        })

        // add normal method
        if (options.keepOriginalMethod) {
            val inputType = context.typeMap.getKotlinType(method.inputType)
            val parameters = if (inputType.isNotProtobufEmpty()) {
                listOf(
                    ParameterInfo(
                        ParameterSpec.builder(Functions.PARAM_REQUEST, inputType).build()
                    )
                )
            } else {
                listOf()
            }
            methods.add(
                createUnaryMethod(
                    context = context,
                    method = method,
                    methodOptions = options,
                    parameters = parameters,
                    requestObject = if (parameters.isNotEmpty()) {
                        CodeBlock.of(Functions.PARAM_REQUEST)
                    } else {
                        CodeBlock.of("%T.getDefaultInstance()", ProtoTypes.EMPTY)
                    }
                )
            )
        }

        return methods.toList()
    }

    private fun createUnaryMethod(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodOptions: MethodOptions,
        parameters: List<ParameterInfo>,
        requestObject: CodeBlock,
        flattenedMethod: FlattenedMethod? = null
    ): TestableFunSpec {
        val name = methodOptions.name.decapitalize()

        // init function
        val m = FunSpec.builder(name)
            .addParameters(parameters.map { it.spec })
            .addModifiers(KModifier.SUSPEND)

        // add request object to documentation
        val extraParamDocs = mutableListOf<CodeBlock>()
        if (flattenedMethod == null) {
            extraParamDocs.add(
                CodeBlock.of(
                    "@param %L the request object for the API call",
                    Functions.PARAM_REQUEST
                )
            )
        }

        // build method body
        when {
            method.isLongRunningOperation(context.proto) -> {
                val realResponseType = getLongRunningResponseType(context, method, methodOptions.longRunningResponse)
                val returnType = GrpcTypes.Support.LongRunningCall(realResponseType)

                m.returns(returnType)
                m.addCode(
                    """
                    |return coroutineScope·{
                    |    %T(
                    |        %N.%N,
                    |        async·{
                    |            %N.%N.execute(context·=·%S)·{
                    |                it.%L(
                    |                    %L
                    |                )
                    |            }
                    |        },
                    |        %T::class.java
                    |    )
                    |}
                    |""".trimMargin(),
                    returnType,
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_OPERATION,
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name,
                    name, requestObject.indent(3),
                    realResponseType
                )
            }
            methodOptions.pagedResponse != null -> {
                val outputType = context.typeMap.getKotlinType(method.outputType)
                val responseListItemType = getResponseListElementType(context, method, methodOptions.pagedResponse)
                val pageType = GrpcTypes.Support.PageWithMetadata(responseListItemType)

                // getters and setters for setting the page sizes, etc.
                val pageTokenSetter =
                    FieldNamer.getJavaBuilderRawSetterName(methodOptions.pagedResponse.requestPageToken)
                val nextPageTokenGetter = FieldNamer.getFieldName(methodOptions.pagedResponse.responsePageToken)
                val responseListGetter =
                    FieldNamer.getJavaBuilderAccessorRepeatedName(methodOptions.pagedResponse.responseList)

                // build method body using a pager
                m.returns(ReceiveChannel::class.asClassName().parameterizedBy(pageType))
                m.addCode(
                    """
                    |return pager(
                    |    method·=·{·request·->
                    |        %N.%N.execute(context·=·%S)·{
                    |            it.%L(request)
                    |        }
                    |    },
                    |    initialRequest·=·{
                    |        %L
                    |    },
                    |    nextRequest·=·{·request,·token·->
                    |        request.toBuilder().%L(token).build()
                    |    },
                    |    nextPage·=·{·response:·%T·->
                    |        %T(
                    |            response.body.%L,
                    |            response.body.%L,
                    |            response.metadata
                    |        )
                    |    }
                    |)
                    |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name,
                    name,
                    requestObject.indent(2),
                    pageTokenSetter,
                    GrpcTypes.Support.CallResult(outputType),
                    pageType,
                    responseListGetter, nextPageTokenGetter
                )
            }
            else -> {
                val originalReturnType = context.typeMap.getKotlinType(method.outputType)
                val returnsNothing = originalReturnType.isProtobufEmpty()
                m.returns(
                    GrpcTypes.Support.CallResult(
                        if (returnsNothing) UNIT else originalReturnType
                    )
                )

                if (flattenedMethod?.parameters?.size ?: 0 > 1) {
                    m.addCode(
                        """
                        |return %N.%N.execute(context·=·%S)·{
                        |    it.%L(
                        |        %L
                        |    )
                        |}%L
                        |""".trimMargin(),
                        Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name,
                        name, requestObject.indent(2),
                        if (returnsNothing) ".map { Unit }" else ""
                    )
                } else {
                    m.addCode(
                        """
                        |return %N.%N.execute(context·=·%S)·{
                        |    it.%L(%L)
                        |}%L
                        |""".trimMargin(),
                        Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name,
                        name, requestObject.indent(1),
                        if (returnsNothing) ".map { Unit }" else ""
                    )
                }
            }
        }

        // add documentation
        m.addKdoc(
            documentation.generateMethodKDoc(
                context = context,
                method = method,
                methodOptions = methodOptions,
                parameters = parameters,
                flatteningConfig = flattenedMethod,
                extras = extraParamDocs
            )
        )

        // add unit test
        val test = unitTest.createUnaryMethodUnitTest(
            context, method, methodOptions, parameters, flattenedMethod
        )

        return TestableFunSpec(m.build(), test)
    }

    private fun createStreamingMethods(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodOptions: MethodOptions
    ): List<TestableFunSpec> {
        val name = methodOptions.name.decapitalize()
        val methods = mutableListOf<TestableFunSpec>()

        // input / output types
        val normalInputType = context.typeMap.getKotlinType(method.inputType)
        val normalOutputType = context.typeMap.getKotlinType(method.outputType)

        // add flattened methods
        methods.addAll(methodOptions.flattenedMethods.map { flattenedMethod ->
            val (parameters, request) = getFlattenedParameters(context, method, flattenedMethod)

            val flattened = FunSpec.builder(name)
            if (method.hasClientStreaming() && method.hasServerStreaming()) {
                flattened.addKdoc(
                    documentation.generateMethodKDoc(
                        context = context,
                        method = method,
                        methodOptions = methodOptions,
                        flatteningConfig = flattenedMethod,
                        parameters = parameters
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
                    |return %N.%N.prepare·{
                    |    withInitialRequest(
                    |        %L
                    |    )
                    |}.executeStreaming(context·=·%S)·{ it::%N }
                    |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API,
                    request.indent(2),
                    name, name
                )
            } else if (method.hasClientStreaming()) { // client only
                flattened.addKdoc(
                    documentation.generateMethodKDoc(
                        context = context,
                        method = method,
                        methodOptions = methodOptions,
                        flatteningConfig = flattenedMethod,
                        parameters = parameters
                    )
                )
                flattened.returns(
                    GrpcTypes.Support.ClientStreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                flattened.addCode(
                    "return %N.%N.executeClientStreaming(context·=·%S)·{ it::%N }\n",
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name, name
                )
            } else if (method.hasServerStreaming()) { // server only
                flattened.addKdoc(
                    documentation.generateMethodKDoc(
                        context = context,
                        method = method,
                        methodOptions = methodOptions,
                        flatteningConfig = flattenedMethod,
                        parameters = parameters
                    )
                )
                flattened.addParameters(parameters.map { it.spec })
                flattened.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                flattened.addCode(
                    """
                    |return %N.%N.executeServerStreaming(context·=·%S)·{·stub,·observer·->
                    |    stub.%N(
                    |        %L,
                    |        observer
                    |    )
                    |}
                    |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name,
                    name, request.indent(2)
                )
            } else {
                throw IllegalArgumentException("Unknown streaming type (not client or server)!")
            }

            // generate test
            val test = unitTest.createStreamingMethodTest(
                context, method, methodOptions, parameters, flattenedMethod
            )

            TestableFunSpec(flattened.build(), test)
        })

        // unchanged method
        if (methodOptions.keepOriginalMethod) {
            val normal = FunSpec.builder(name)
                .addKdoc(documentation.generateMethodKDoc(context, method, methodOptions))
            val parameters = mutableListOf<ParameterInfo>()

            if (method.hasClientStreaming() && method.hasServerStreaming()) {
                normal.returns(
                    GrpcTypes.Support.StreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                normal.addCode(
                    "return %N.%N.executeStreaming(context·=·%S)·{ it::%N }\n",
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name, name
                )
            } else if (method.hasClientStreaming()) { // client only
                normal.returns(
                    GrpcTypes.Support.ClientStreamingCall(
                        normalInputType, normalOutputType
                    )
                )
                normal.addCode(
                    "return %N.%N.executeClientStreaming(context·=·%S)·{ it::%N }\n",
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name, name
                )
            } else if (method.hasServerStreaming()) { // server only
                val param = ParameterSpec.builder(Functions.PARAM_REQUEST, normalInputType).build()
                parameters.add(ParameterInfo(param))
                normal.addParameter(param)
                normal.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                normal.addCode(
                    """
                    |return %N.%N.executeServerStreaming(context·=·%S)·{·stub,·observer·->
                    |    stub.%N(%N, observer)
                    |}
                    |""".trimMargin(),
                    Properties.PROP_STUBS, Stubs.PROP_STUBS_API, name,
                    name, Functions.PARAM_REQUEST
                )
            } else {
                throw IllegalArgumentException("Unknown streaming type (not client or server)!")
            }

            // generate test
            val test =
                unitTest.createStreamingMethodTest(
                    context, method, methodOptions, parameters.toList(), null
                )

            methods.add(TestableFunSpec(normal.build(), test))
        }

        return methods.toList()
    }
}
