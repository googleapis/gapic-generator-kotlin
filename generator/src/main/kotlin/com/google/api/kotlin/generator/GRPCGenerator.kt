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

import com.google.api.kotlin.ClientGenerator
import com.google.api.kotlin.GeneratedArtifact
import com.google.api.kotlin.GeneratedSource
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.TestableFunSpec
import com.google.api.kotlin.asTestable
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.SampleMethod
import com.google.api.kotlin.types.GrpcTypes
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import mu.KotlinLogging
import java.io.InputStream

private val log = KotlinLogging.logger {}

// property, param names, etc. for the generated client
private const val PROP_CHANNEL = "channel"
private const val PROP_CALL_OPTS = "options"
private const val PROP_STUBS = "stubs"
private const val PROP_STUBS_STREAM = "stream"
private const val PROP_STUBS_FUTURE = "future"
private const val PROP_STUBS_OPERATION = "operation"
private const val CONST_ALL_SCOPES = "ALL_SCOPES"
private const val PARAM_REQUEST = "request"
private const val PARAM_FACTORY = "factory"
private const val FUN_PREPARE = "prepare"
private const val STUBS_CLASS_TYPE = "Stubs"

private const val PLACEHOLDER_KEYFILE = "< keyfile >"

/**
 * Generates a gRPC client.
 *
 * @author jbolinger
 */
internal class GRPCGenerator : AbstractGenerator(), ClientGenerator {

    override fun generateServiceClient(ctx: GeneratorContext): List<GeneratedArtifact> {
        val artifacts = mutableListOf<GeneratedArtifact>()

        val clientType = TypeSpec.classBuilder(ctx.className)
        val apiMethods = Functions.generate(ctx)

        // build client
        clientType.addAnnotation(createGeneratedByAnnotation())
        clientType.superclass(GrpcTypes.Support.GrpcClient)
        clientType.addSuperclassConstructorParameter("%N", PROP_CHANNEL)
        clientType.addSuperclassConstructorParameter("%N", PROP_CALL_OPTS)
        clientType.addKdoc(Documentation.generateClassDoc(ctx))
        clientType.primaryConstructor(Constructor.generatePrimary())
        clientType.addProperties(Properties.generate(ctx))
        clientType.addFunctions(apiMethods.map { it.function })
        clientType.addType(Companion.generate(ctx))
        clientType.addType(Stubs.generateHolderType(ctx))

        // add statics
        val imports = listOf("pager")
            .map { ClassName(GrpcTypes.Support.SUPPORT_LIB_PACKAGE, it) }
        val grpcImports = listOf("prepare")
            .map { ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, it) }

        // add client type
        artifacts.add(
            GeneratedSource(
                ctx.className.packageName,
                ctx.className.simpleName,
                types = listOf(clientType.build()),
                imports = imports + grpcImports
            )
        )

        // build unit tests
        UnitTests.generate(ctx, apiMethods)?.let {
            artifacts.add(it)
        }

        // all done!
        return artifacts.toList()
    }

    /** the top level (class) comment */
    object Documentation : AbstractGenerator() {
        fun generateClassDoc(ctx: GeneratorContext): CodeBlock {
            val doc = CodeBlock.builder()
            val m = ctx.metadata

            // add primary (summary) section
            doc.add(
                """
                |%L
                |
                |%L
                |
                |[Product Documentation](%L)
                |""".trimMargin(),
                m.branding.name, m.branding.summary.wrap(), m.branding.url
            )

            // TODO: add other sections (quick start, etc.)

            return doc.build()
        }
    }

    /** constructor for generated client  */
    object Constructor : AbstractGenerator() {
        fun generatePrimary(): FunSpec {
            return FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter(PROP_CHANNEL, GrpcTypes.ManagedChannel)
                .addParameter(PROP_CALL_OPTS, GrpcTypes.Support.ClientCallOptions)
                .addParameter(
                    ParameterSpec.builder(
                        PARAM_FACTORY, ClassName("", STUBS_CLASS_TYPE, "Factory").asNullable()
                    ).defaultValue("null").build()
                ).build()
        }
    }

    object Properties : AbstractGenerator() {
        fun generate(ctx: GeneratorContext): List<PropertySpec> {
            val grpcType = ctx.typeMap.getKotlinGrpcType(
                ctx.proto, ctx.service, "Grpc"
            )

            val stub = PropertySpec.builder(
                PROP_STUBS, ClassName.bestGuess(STUBS_CLASS_TYPE)
            )
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    CodeBlock.builder()
                        .add(
                            "%N?.create(%N, %N) ?: %T(\n",
                            PARAM_FACTORY,
                            PROP_CHANNEL,
                            PROP_CALL_OPTS,
                            ClassName.bestGuess(STUBS_CLASS_TYPE)
                        )
                        .add(
                            "%T.newStub(%N).prepare(%N),\n",
                            grpcType, PROP_CHANNEL, PROP_CALL_OPTS
                        )
                        .add(
                            "%T.newFutureStub(%N).prepare(%N),\n",
                            grpcType, PROP_CHANNEL, PROP_CALL_OPTS
                        )
                        .add(
                            "%T.newFutureStub(%N).prepare(%N))",
                            GrpcTypes.OperationsGrpc, PROP_CHANNEL, PROP_CALL_OPTS
                        )
                        .build()
                )
                .build()

            return listOf(stub)
        }
    }

    object Functions : AbstractGenerator() {
        fun generate(ctx: GeneratorContext): List<TestableFunSpec> {
            // we'll use this in the example text
            val firstMethodName = ctx.service.methodList
                .firstOrNull()?.name?.decapitalize()

            // extra methods (not part of the target API)
            val extras = listOf(
                FunSpec.builder(FUN_PREPARE)
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
                        ctx.className, PLACEHOLDER_KEYFILE, firstMethodName ?: "method"
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
                        GrpcTypes.Support.ClientCallOptionsBuilder, PROP_CALL_OPTS
                    )
                    .addStatement("options.init()")
                    .addStatement(
                        "return %T(%N, options.build())",
                        ctx.className, PROP_CHANNEL
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
                    ParameterSpec.builder(
                        PARAM_REQUEST,
                        ctx.typeMap.getKotlinType(method.inputType)
                    ).build()
                )
                methods.add(
                    createUnaryMethod(
                        ctx, method, methodName,
                        parameters = parameters,
                        requestObject = CodeBlock.of(PARAM_REQUEST),
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
            parameters: List<ParameterSpec>,
            requestObject: CodeBlock,
            flatteningConfig: FlattenedMethod? = null,
            paging: PagedResponse? = null,
            samples: List<SampleMethod> = listOf()
        ): TestableFunSpec {
            val m = FunSpec.builder(methodName)
                .addParameters(parameters)

            val extraParamDocs = mutableListOf<CodeBlock>()

            // add request object to documentation
            if (flatteningConfig == null) {
                extraParamDocs.add(
                    CodeBlock.of(
                        "@param %L the request object for the API call",
                        PARAM_REQUEST
                    )
                )
            }

            // TODO: placeholder for now...
            // inputs/method call are always the same (mocks of all inputs)
            val unitTestGivens = parameters.map {
                val name = "the${it.name.capitalize()}"
                Pair(name, CodeBlock.of("val $name: %T = mock()", it.type))
            }
            val unitTestWhen = CodeBlock.of(
                """
                |val client = %N()
                |client.%N(${parameters.joinToString(", ") { "%N" }})
                |""".trimMargin(),
                UnitTests.FUN_GET_CLIENT,
                methodName, *unitTestGivens.map { it.first }.toTypedArray())
            var unitTestThen = CodeBlock.of("throw Exception()")

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
                        PROP_STUBS, PROP_STUBS_OPERATION,
                        PROP_STUBS, PROP_STUBS_FUTURE,
                        methodName, requestObject,
                        realResponseType
                    )
                }
                paging != null -> {
                    val inputType = ctx.typeMap.getKotlinType(method.inputType)
                    val outputType = ctx.typeMap.getKotlinType(method.outputType)
                    val requestType = getResponseListElementType(ctx, method, paging)

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
                            GrpcTypes.Support.CallResult(outputType), requestType
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
                        PROP_STUBS,
                        PROP_STUBS_FUTURE,
                        methodName,
                        pageSizeSetter,
                        requestObject,
                        pageTokenSetter,
                        GrpcTypes.Support.PageResult(requestType),
                        responseListGetter,
                        nextPageTokenGetter
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
                        PROP_STUBS, PROP_STUBS_FUTURE,
                        methodName, requestObject
                    )

                    unitTestThen = if (flatteningConfig != null) {
                        CodeBlock.of("")
                    } else {
                        CodeBlock.of(
                            """
                            |verify(%N).executeFuture<%T>(check {
                            |    val mock: %T = mock()
                            |    it(mock)
                            |    verify(mock).%N(eq(%N))
                            |})
                            |""".trimMargin(),
                            UnitTests.MOCK_FUTURE_STUB, originalReturnType,
                            Stubs.getFutureStubType(ctx).typeArguments.first(),
                            methodName,
                            unitTestGivens.first().first)
                    }
                }
            }

            // add documentation
            m.addKdoc(createMethodDoc(
                ctx,
                method,
                methodName,
                samples,
                flatteningConfig,
                parameters,
                extraParamDocs
            ))

            // create unit test
            val unitTest = CodeBlock.of(
                """
                |${unitTestGivens.joinToString("\n") { "%L" }}
                |
                |%L
                |%L""".trimMargin(),
                *unitTestGivens.map { it.second }.toTypedArray(),
                unitTestWhen,
                unitTestThen)

            return TestableFunSpec(m.build(), unitTest)
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
            methods.addAll(options.flattenedMethods.map {
                val (parameters, request) = getFlattenedParameters(ctx, method, it)

                val flattened = FunSpec.builder(methodName)
                if (method.hasClientStreaming() && method.hasServerStreaming()) {
                    flattened.addKdoc(
                        createMethodDoc(
                            ctx,
                            method,
                            methodName,
                            options.samples,
                            it,
                            parameters
                        )
                    )
                    flattened.addParameters(parameters)
                    flattened.returns(
                        GrpcTypes.Support.StreamingCall(
                            normalInputType,
                            normalOutputType
                        )
                    )
                    flattened.addCode(
                        """
                        |val stream = %N.%N.executeStreaming { it::%N }
                        |stream.requests.send(%L)
                        |return stream
                        |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_STREAM, methodName, request
                    )
                } else if (method.hasClientStreaming()) { // client only
                    flattened.addKdoc(createMethodDoc(ctx, method, methodName, options.samples, it))
                    flattened.returns(
                        GrpcTypes.Support.ClientStreamingCall(
                            normalInputType,
                            normalOutputType
                        )
                    )
                    flattened.addCode(
                        "return %N.%N.executeClientStreaming { it::%N }",
                        PROP_STUBS, PROP_STUBS_STREAM, methodName
                    )
                } else if (method.hasServerStreaming()) { // server only
                    flattened.addKdoc(
                        createMethodDoc(
                            ctx,
                            method,
                            methodName,
                            options.samples,
                            it,
                            parameters
                        )
                    )
                    flattened.addParameters(parameters)
                    flattened.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                    flattened.addCode(
                        """
                        |return %N.%N.executeServerStreaming { stub, observer ->
                        |  stub.%N(%L, observer)
                        |}
                        |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_STREAM, methodName, request
                    )
                } else {
                    throw IllegalArgumentException("Unknown streaming type (not client or server)!")
                }
                TestableFunSpec(flattened.build(), CodeBlock.of("throw Exception()"))
            })

            // unchanged method
            if (options.keepOriginalMethod) {
                val normal = FunSpec.builder(methodName)
                    .addKdoc(createMethodDoc(ctx, method, methodName, options.samples))
                if (method.hasClientStreaming() && method.hasServerStreaming()) {
                    normal.returns(
                        GrpcTypes.Support.StreamingCall(
                            normalInputType,
                            normalOutputType
                        )
                    )
                    normal.addCode(
                        "return %N.%N.executeStreaming { it::%N }",
                        PROP_STUBS, PROP_STUBS_STREAM, methodName
                    )
                } else if (method.hasClientStreaming()) { // client only
                    normal.returns(
                        GrpcTypes.Support.ClientStreamingCall(
                            normalInputType,
                            normalOutputType
                        )
                    )
                    normal.addCode(
                        "return %N.%N.executeClientStreaming { it::%N }",
                        PROP_STUBS, PROP_STUBS_STREAM, methodName
                    )
                } else if (method.hasServerStreaming()) { // server only
                    normal.addParameter(PARAM_REQUEST, normalInputType)
                    normal.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                    normal.addCode(
                        """
                        |return %N.%N.executeServerStreaming { stub, observer ->
                        |  stub.%N(%N, observer)
                        |}
                        |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_STREAM, methodName, PARAM_REQUEST
                    )
                } else {
                    throw IllegalArgumentException("Unknown streaming type (not client or server)!")
                }
                methods.add(TestableFunSpec(normal.build(), CodeBlock.of("throw Exception()")))
            }

            return methods.toList()
        }

        // create method comments from proto comments
        private fun createMethodDoc(
            ctx: GeneratorContext,
            method: DescriptorProtos.MethodDescriptorProto,
            methodName: String,
            samples: List<SampleMethod>,
            flatteningConfig: FlattenedMethod? = null,
            parameters: List<ParameterSpec> = listOf(),
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
                Pair(parameters[idx].name, cleanupComment(comment))
            }?.filter { it.second != null } ?: listOf()
            paramComments.forEach { doc.add("\n@param %L %L\n", it.first, it.second) }

            // add any extra comments at the bottom (only used for the pageSize currently)
            extras.forEach { doc.add("\n%L\n", it) }

            // put it all together
            return doc.build()
        }
    }

    // TODO: Samples?
//    private fun createMethodDocSample(ctx: GeneratorContext,
//                                      method: DescriptorProtos.MethodDescriptorProto,
//                                      methodName: String,
//                                      sample: SampleMethod,
//                                      config: FlattenedMethod?): CodeBlock {
//    }

    object Companion : AbstractGenerator() {
        fun generate(ctx: GeneratorContext): TypeSpec {
            return TypeSpec.companionObjectBuilder()
                .addKdoc(
                    "Utilities for creating a fully configured %N.\n",
                    ctx.className.simpleName
                )
                .addProperty(
                    PropertySpec.builder(
                        CONST_ALL_SCOPES, List::class.parameterizedBy(String::class)
                    )
                        .addAnnotation(JvmStatic::class)
                        .initializer("listOf(%L)", ctx.metadata.scopesAsLiteral)
                        .build()
                )
                .addFunctions(createClientFactories(ctx))
                .build()
        }

        // client factory methods for creating client instances via various means
        // (i.e. service accounts, access tokens, etc.)
        private fun createClientFactories(ctx: GeneratorContext): List<FunSpec> {
            val fromAccessToken = FunSpec.builder("fromAccessToken")
                .addKdoc(
                    """
                    |Create a %N with the provided [accessToken].
                    |
                    |TODO: ADD INFO ABOUT REFRESHING
                    |
                    |If a [channel] is not provided one will be created automatically (recommended).
                    |""".trimMargin(), ctx.className.simpleName
                )
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter("accessToken", GrpcTypes.Auth.AccessToken)
                .addParameter(
                    ParameterSpec.builder(
                        "scopes",
                        List::class.parameterizedBy(String::class)
                    )
                        .defaultValue("%N", CONST_ALL_SCOPES)
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable()
                    )
                        .defaultValue("null")
                        .build()
                )
                .returns(ctx.className)
                .addStatement(
                    "val credentials = %T.create(accessToken).createScoped(scopes)",
                    GrpcTypes.Auth.GoogleCredentials
                )
                .addStatement(
                    "return %T(channel ?: createChannel(), %T(%T.from(credentials)))",
                    ctx.className,
                    GrpcTypes.Support.ClientCallOptions,
                    GrpcTypes.Auth.MoreCallCredentials
                )
                .build()

            val fromServiceAccount = FunSpec.builder("fromServiceAccount")
                .addKdoc(
                    """
                    |Create a %N with service account credentials from a JSON [keyFile].
                    |
                    |If a [channel] is not provided one will be created automatically (recommended).
                    |""".trimMargin(), ctx.className.simpleName
                )
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter("keyFile", InputStream::class)
                .addParameter(
                    ParameterSpec.builder(
                        "scopes",
                        List::class.parameterizedBy(String::class)
                    )
                        .defaultValue("%N", CONST_ALL_SCOPES)
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable()
                    )
                        .defaultValue("null")
                        .build()
                )
                .returns(ctx.className)
                .addStatement(
                    "val credentials = %T.fromStream(keyFile).createScoped(scopes)",
                    GrpcTypes.Auth.GoogleCredentials
                )
                .addStatement(
                    "return %T(channel ?: createChannel(), %T(%T.from(credentials)))",
                    ctx.className,
                    GrpcTypes.Support.ClientCallOptions,
                    GrpcTypes.Auth.MoreCallCredentials
                )
                .build()

            val fromCredentials = FunSpec.builder("fromCredentials")
                .addKdoc(
                    """
                    |Create a %N with the provided [credentials].
                    |
                    |If a [channel] is not provided one will be created automatically (recommended).
                    |""".trimMargin(), ctx.className.simpleName
                )
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter("credentials", GrpcTypes.Auth.GoogleCredentials)
                .addParameter(
                    ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable()
                    )
                        .defaultValue("null")
                        .build()
                )
                .returns(ctx.className)
                .addStatement(
                    "return %T(channel ?: createChannel(), %T(%T.from(credentials)))",
                    ctx.className,
                    GrpcTypes.Support.ClientCallOptions,
                    GrpcTypes.Auth.MoreCallCredentials
                )
                .build()

            val fromStubs = FunSpec.builder("fromStubs")
                .addKdoc(
                    """
                    |Create a %N with the provided gRPC stubs.
                    |
                    |This is an advanced method and should only be used when you need complete
                    |control over the underlying gRPC stubs that are used by this client.
                    |
                    |Prefer to use [fromAccessToken], [fromServiceAccount], or [fromCredentials].
                    |""".trimMargin(), ctx.className.simpleName
                )
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter(PARAM_FACTORY, ClassName("", STUBS_CLASS_TYPE, "Factory"))
                .addParameter(
                    ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable()
                    )
                        .defaultValue("null")
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder(
                        "options", GrpcTypes.Support.ClientCallOptions.asNullable()
                    )
                        .defaultValue("null")
                        .build()
                )
                .returns(ctx.className)
                .addStatement(
                    "return %T(channel ?: createChannel(), options ?: %T(), %N)",
                    ctx.className,
                    GrpcTypes.Support.ClientCallOptions,
                    PARAM_FACTORY
                )
                .build()

            val createChannel = FunSpec.builder("createChannel")
                .addKdoc(
                    """
                    |Create a [ManagedChannel] to use with a %N.
                    |
                    |Prefer to use the default value with [fromAccessToken], [fromServiceAccount],
                    |or [fromCredentials] unless you need to customize the channel.
                    |""".trimMargin(), ctx.className.simpleName
                )
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter(
                    ParameterSpec.builder("host", String::class)
                        .defaultValue("%S", ctx.metadata.host)
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder("port", Int::class)
                        .defaultValue("443")
                        .build()
                )
                .addParameter(
                    ParameterSpec.builder("enableRetry", Boolean::class)
                        .defaultValue("true")
                        .build()
                )
                .returns(GrpcTypes.ManagedChannel)
                .addStatement(
                    "val builder = %T.forAddress(host, port)",
                    GrpcTypes.OkHttpChannelBuilder
                )
                .beginControlFlow("if (enableRetry)")
                .addStatement("builder.enableRetry()")
                .endControlFlow()
                .addStatement("return builder.build()")
                .build()

            return listOf(
                fromAccessToken,
                fromServiceAccount,
                fromCredentials,
                fromStubs,
                createChannel
            )
        }
    }

    // creates a nested type that will be used to hold the gRPC stubs used by the client
    object Stubs : AbstractGenerator() {
        fun generateHolderType(ctx: GeneratorContext): TypeSpec {
            val streamType = getStreamStubType(ctx)
            val futureType = getFutureStubType(ctx)
            val opType = getOperationsStubType(ctx)

            return TypeSpec.classBuilder(STUBS_CLASS_TYPE)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter(PROP_STUBS_STREAM, streamType)
                        .addParameter(PROP_STUBS_FUTURE, futureType)
                        .addParameter(PROP_STUBS_OPERATION, opType)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder(PROP_STUBS_STREAM, streamType)
                        .initializer(PROP_STUBS_STREAM)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder(PROP_STUBS_FUTURE, futureType)
                        .initializer(PROP_STUBS_FUTURE)
                        .build()
                )
                .addProperty(
                    PropertySpec.builder(PROP_STUBS_OPERATION, opType)
                        .initializer(PROP_STUBS_OPERATION)
                        .build()
                )
                .addType(
                    TypeSpec.interfaceBuilder("Factory")
                        .addFunction(
                            FunSpec.builder("create")
                                .addModifiers(KModifier.ABSTRACT)
                                .returns(ClassName("", STUBS_CLASS_TYPE))
                                .addParameter(PROP_CHANNEL, GrpcTypes.ManagedChannel)
                                .addParameter(PROP_CALL_OPTS, GrpcTypes.Support.ClientCallOptions)
                                .build()
                        )
                        .build()
                )
                .build()
        }

        fun getStreamStubType(ctx: GeneratorContext) = GrpcTypes.Support.GrpcClientStub(
            ctx.typeMap.getKotlinGrpcTypeInnerClass(
                ctx.proto, ctx.service, "Grpc", "${ctx.service.name}Stub"
            )
        )

        fun getFutureStubType(ctx: GeneratorContext) = GrpcTypes.Support.GrpcClientStub(
            ctx.typeMap.getKotlinGrpcTypeInnerClass(
                ctx.proto, ctx.service, "Grpc", "${ctx.service.name}FutureStub"
            )
        )

        fun getOperationsStubType(ctx: GeneratorContext) =
            GrpcTypes.Support.GrpcClientStub(GrpcTypes.OperationsFutureStub)
    }

    object UnitTests : AbstractGenerator() {
        const val VAL_CLIENT = "client"
        const val FUN_GET_CLIENT = "getClient"
        const val MOCK_STREAM_STUB = "streamingStub"
        const val MOCK_FUTURE_STUB = "futureStub"
        const val MOCK_OPS_STUB = "operationsStub"
        const val MOCK_CHANNEL = "channel"
        const val MOCK_CALL_OPTS = "options"

        fun generate(ctx: GeneratorContext, apiMethods: List<TestableFunSpec>): GeneratedSource? {
            val name = "${ctx.className.simpleName}Test"
            val unitTestType = TypeSpec.classBuilder(name)

            // add props (mocks)
            val mocks = mapOf(
                MOCK_STREAM_STUB to Stubs.getStreamStubType(ctx),
                MOCK_FUTURE_STUB to Stubs.getFutureStubType(ctx),
                MOCK_OPS_STUB to Stubs.getOperationsStubType(ctx),
                MOCK_CHANNEL to GrpcTypes.ManagedChannel,
                MOCK_CALL_OPTS to GrpcTypes.Support.ClientCallOptions
            )
            for ((name, type) in mocks) {
                unitTestType.addProperty(
                    PropertySpec.builder(name, type)
                        .initializer("mock()")
                        .build()
                )
            }

            // add functions
            unitTestType.addFunction(
                FunSpec.builder("resetMocks")
                    .addAnnotation(ClassName("kotlin.test", "BeforeTest"))
                    .addStatement(
                        "reset(%N, %N, %N, %N, %N)", *mocks.keys.toTypedArray())
                    .build()
            )
            unitTestType.addFunction(
                FunSpec.builder(FUN_GET_CLIENT)
                    .returns(ctx.className)
                    .addStatement(
                        """
                        |return %T.fromStubs(object: %T.%L.Factory {
                        |    override fun create(channel: %T, options: %T) =
                        |        %T.%L(%N, %N, %N)
                        |}, %N, %N)
                        |""".trimMargin(),
                        ctx.className, ctx.className, STUBS_CLASS_TYPE,
                        GrpcTypes.ManagedChannel, GrpcTypes.Support.ClientCallOptions,
                        ctx.className, STUBS_CLASS_TYPE,
                        MOCK_STREAM_STUB, MOCK_FUTURE_STUB, MOCK_OPS_STUB,
                        MOCK_CHANNEL, MOCK_CALL_OPTS)
                    .build()
            )
            unitTestType.addFunctions(generateFunctions(apiMethods))

            // put it all together
            return if (unitTestType.funSpecs.isNotEmpty()) {
                GeneratedSource(
                    ctx.className.packageName,
                    name,
                    types = listOf(unitTestType.build()),
                    imports = listOf(
                        ClassName("kotlin.test", "assertEquals"),
                        ClassName("com.nhaarman.mockito_kotlin", "reset"),
                        ClassName("com.nhaarman.mockito_kotlin", "mock"),
                        ClassName("com.nhaarman.mockito_kotlin", "verify"),
                        ClassName("com.nhaarman.mockito_kotlin", "check"),
                        ClassName("com.nhaarman.mockito_kotlin", "eq")
                    ),
                    kind = GeneratedSource.Kind.UNIT_TEST
                )
            } else {
                null
            }
        }

        private fun generateFunctions(functions: List<TestableFunSpec>): List<FunSpec> {
            val nameCounter = mutableMapOf<String, Int>()

            return functions
                .filter { it.unitTestCode != null }
                .map {
                    // add a numbered suffix to the name if there are overloads
                    var name = "test${it.function.name.capitalize()}"
                    val suffix = nameCounter[name] ?: 0
                    nameCounter[name] = suffix + 1
                    if (suffix > 0) {
                        name += suffix
                    }

                    // create fun!
                    FunSpec.builder(name)
                        .addAnnotation(ClassName("kotlin.test", "Test"))
                        .addCode(it.unitTestCode!!)
                        .build()
                }
        }
    }
}
