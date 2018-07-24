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

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.GeneratorResponse
import com.google.api.kotlin.generator.config.FlattenedMethod
import com.google.api.kotlin.generator.config.MethodOptions
import com.google.api.kotlin.generator.config.PagedResponse
import com.google.api.kotlin.generator.config.SampleMethod
import com.google.api.kotlin.generator.types.GrpcTypes
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
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
private const val FUN_PREPARE = "prepare"
private const val STUBS_CLASS_TYPE = "Stubs"

private const val PLACEHOLDER_KEYFILE = "< keyfile >"

/**
 * Generates a gRPC client.
 *
 * @author jbolinger
 */
internal class GRPCGenerator : AbstractGenerator() {

    override fun generateServiceClient(ctx: GeneratorContext): GeneratorResponse {
        val type = TypeSpec.classBuilder(ctx.className)

        // build client
        type.addAnnotation(createGeneratedByAnnotation())
        type.addKdoc(createClassKDoc(ctx))
        type.superclass(GrpcTypes.Support.GrpcClient)
        type.addSuperclassConstructorParameter("%N", PROP_CHANNEL)
        type.addSuperclassConstructorParameter("%N", PROP_CALL_OPTS)
        type.primaryConstructor(createPrimaryConstructor(ctx))
        type.addProperties(createProperties(ctx))
        type.addFunctions(createMethods(ctx))
        type.addType(createCompanion(ctx))
        type.addType(createStubHolderType(ctx))

        // add statics
        val imports = listOf("pager")
                .map { ClassName(GrpcTypes.Support.SUPPORT_LIB_PACKAGE, it) }
        val grpcImports = listOf("prepare")
                .map { ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, it) }

        // all done!
        return GeneratorResponse(type.build(), imports + grpcImports)
    }

    /** the top level (class) comment */
    private fun createClassKDoc(ctx: GeneratorContext): CodeBlock {
        val doc = CodeBlock.builder()
        val m = ctx.metadata

        // add primary (summary) section
        doc.add("""
            |%L
            |
            |%L
            |
            |[Product Documentation](%L)
            |""".trimMargin(),
                m.branding.name, m.branding.summary.wrap(), m.branding.url)

        // TODO: add other sections (quick start, etc.)

        return doc.build()
    }

    /** constructor for generated client  */
    private fun createPrimaryConstructor(ctx: GeneratorContext): FunSpec {
        return FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter(PROP_CHANNEL, GrpcTypes.ManagedChannel)
                .addParameter(PROP_CALL_OPTS, GrpcTypes.Support.ClientCallOptions)
                .build()
    }

    private fun createProperties(ctx: GeneratorContext): List<PropertySpec> {
        val grpcType = ctx.typeMap.getKotlinGrpcType(
                ctx.proto, ctx.service, "Grpc")

        val stub = PropertySpec.builder(
                PROP_STUBS, ClassName.bestGuess(STUBS_CLASS_TYPE))
                .addModifiers(KModifier.PRIVATE)
                .initializer(CodeBlock.builder()
                        .add("%T(\n",
                                ClassName.bestGuess(STUBS_CLASS_TYPE))
                        .add("%T.newStub(%N).prepare(%N),\n",
                                grpcType, PROP_CHANNEL, PROP_CALL_OPTS)
                        .add("%T.newFutureStub(%N).prepare(%N),\n",
                                grpcType, PROP_CHANNEL, PROP_CALL_OPTS)
                        .add("%T.newFutureStub(%N).prepare(%N))",
                                GrpcTypes.OperationsGrpc, PROP_CHANNEL, PROP_CALL_OPTS)
                        .build())
                .build()

        return listOf(stub)
    }

    private fun createMethods(ctx: GeneratorContext): List<FunSpec> {
        // we'll use this in the example text
        val firstMethodName = ctx.service.methodList
                .firstOrNull()?.name?.decapitalize()

        // extra methods (not part of the target API)
        val extras = listOf(
                FunSpec.builder(FUN_PREPARE)
                        .addKdoc("""
                            |Prepare for an API call by setting any desired options. For example:
                            |
                            |```
                            |val client = %T.fromServiceAccount(%L)
                            |val response = client.prepare {
                            |  withMetadata("my-custom-header", listOf("some", "thing"))
                            |}.%N(request).get()
                            |```
                            |
                            |If you will make multiple requests with the same settings, simply
                            |save the client returned by this call and reuse it.
                            |""".trimMargin(),
                                ctx.className, PLACEHOLDER_KEYFILE, firstMethodName ?: "method")
                        .returns(ctx.className)
                        .addParameter(ParameterSpec.builder("init",
                                LambdaTypeName.get(
                                        GrpcTypes.Support.ClientCallOptionsBuilder,
                                        listOf(),
                                        Unit::class.asTypeName()))
                                .build())
                        .addStatement("val options = %T(%N)",
                                GrpcTypes.Support.ClientCallOptionsBuilder, PROP_CALL_OPTS)
                        .addStatement("options.init()")
                        .addStatement("return %T(%N, options.build())",
                                ctx.className, PROP_CHANNEL)
                        .build()
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
    ): List<FunSpec> {
        val methods = mutableListOf<FunSpec>()

        // add flattened methods
        methods.addAll(options.flattenedMethods.map { flattenedMethod ->
            val (parameters, request) = getFlattenedParameters(ctx, method, flattenedMethod)
            createUnaryMethod(ctx, method, methodName,
                    parameters = parameters,
                    requestObject = request,
                    flatteningConfig = flattenedMethod,
                    paging = options.pagedResponse,
                    samples = options.samples)
        })

        // add normal method
        if (options.keepOriginalMethod) {
            val parameters = listOf(
                    ParameterSpec.builder(PARAM_REQUEST, ctx.typeMap.getKotlinType(method.inputType))
                            .build())
            methods.add(createUnaryMethod(ctx, method, methodName,
                    parameters = parameters,
                    requestObject = CodeBlock.of(PARAM_REQUEST),
                    paging = options.pagedResponse,
                    samples = options.samples))
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
    ): FunSpec {
        val m = FunSpec.builder(methodName)
                .addParameters(parameters)

        val extraParamDocs = mutableListOf<CodeBlock>()

        // add request object to documentation
        if (flatteningConfig == null) {
            extraParamDocs.add(CodeBlock.of("@param %L the request object for the API call", PARAM_REQUEST))
        }

        // build method body
        when {
            method.isLongRunningOperation() -> {
                val returnType = GrpcTypes.Support.LongRunningCall(getLongRunningResponseType(ctx, method))
                val realResponseType = getLongRunningResponseType(ctx, method)

                m.returns(returnType)
                m.addCode("""
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
                        realResponseType)
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
                m.addParameter(ParameterSpec.builder("pageSize", Int::class)
                        .defaultValue("20")
                        .build())

                // build method body using a pager
                m.returns(GrpcTypes.Support.Pager(inputType,
                        GrpcTypes.Support.CallResult(outputType), requestType))
                m.addCode("""
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
                        PROP_STUBS, PROP_STUBS_FUTURE,
                        methodName, pageSizeSetter,
                        requestObject,
                        pageTokenSetter,
                        GrpcTypes.Support.PageResult(requestType), responseListGetter, nextPageTokenGetter)
            }
            else -> {
                m.returns(GrpcTypes.Support.FutureCall(ctx.typeMap.getKotlinType(method.outputType)))
                m.addCode("""
                        |return %N.%N.executeFuture {
                        |  it.%L(%L)
                        |}
                        |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_FUTURE,
                        methodName, requestObject)
            }
        }

        // finish up and add documentation
        return m.addKdoc(createMethodDoc(ctx, method, methodName, samples, flatteningConfig, parameters, extraParamDocs))
                .build()
    }

    private fun createStreamingMethods(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        options: MethodOptions
    ): List<FunSpec> {
        val methods = mutableListOf<FunSpec>()

        // input / output types
        val normalInputType = ctx.typeMap.getKotlinType(method.inputType)
        val normalOutputType = ctx.typeMap.getKotlinType(method.outputType)

        // add flattened methods
        methods.addAll(options.flattenedMethods.map {
            val (parameters, request) = getFlattenedParameters(ctx, method, it)

            val flattened = FunSpec.builder(methodName)
            if (method.hasClientStreaming() && method.hasServerStreaming()) {
                flattened.addKdoc(createMethodDoc(ctx, method, methodName, options.samples, it, parameters))
                flattened.addParameters(parameters)
                flattened.returns(GrpcTypes.Support.StreamingCall(normalInputType, normalOutputType))
                flattened.addCode("""
                    |val stream = %N.%N.executeStreaming { it::%N }
                    |stream.requests.send(%L)
                    |return stream
                    |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_STREAM, methodName, request)
            } else if (method.hasClientStreaming()) { // client only
                flattened.addKdoc(createMethodDoc(ctx, method, methodName, options.samples, it))
                flattened.returns(GrpcTypes.Support.ClientStreamingCall(normalInputType, normalOutputType))
                flattened.addCode("return %N.%N.executeClientStreaming { it::%N }",
                        PROP_STUBS, PROP_STUBS_STREAM, methodName)
            } else if (method.hasServerStreaming()) { // server only
                flattened.addKdoc(createMethodDoc(ctx, method, methodName, options.samples, it, parameters))
                flattened.addParameters(parameters)
                flattened.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                flattened.addCode("""
                    |return %N.%N.executeServerStreaming { stub, observer ->
                    |  stub.%N(%L, observer)
                    |}
                    |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_STREAM, methodName, request)
            } else {
                throw IllegalArgumentException("Unknown streaming type (not client or server)!")
            }
            flattened.build()
        })

        // unchanged method
        if (options.keepOriginalMethod) {
            val normal = FunSpec.builder(methodName)
                    .addKdoc(createMethodDoc(ctx, method, methodName, options.samples))
            if (method.hasClientStreaming() && method.hasServerStreaming()) {
                normal.returns(GrpcTypes.Support.StreamingCall(normalInputType, normalOutputType))
                normal.addCode("return %N.%N.executeStreaming { it::%N }",
                        PROP_STUBS, PROP_STUBS_STREAM, methodName)
            } else if (method.hasClientStreaming()) { // client only
                normal.returns(GrpcTypes.Support.ClientStreamingCall(normalInputType, normalOutputType))
                normal.addCode("return %N.%N.executeClientStreaming { it::%N }",
                        PROP_STUBS, PROP_STUBS_STREAM, methodName)
            } else if (method.hasServerStreaming()) { // server only
                normal.addParameter(PARAM_REQUEST, normalInputType)
                normal.returns(GrpcTypes.Support.ServerStreamingCall(normalOutputType))
                normal.addCode("""
                    |return %N.%N.executeServerStreaming { stub, observer ->
                    |  stub.%N(%N, observer)
                    |}
                    |""".trimMargin(),
                        PROP_STUBS, PROP_STUBS_STREAM, methodName, PARAM_REQUEST)
            } else {
                throw IllegalArgumentException("Unknown streaming type (not client or server)!")
            }
            methods.add(normal.build())
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
                    ctx, path, ctx.typeMap.getProtoTypeDescriptor(method.inputType))
            val comment = fieldInfo.file.getParameterComments(fieldInfo)
            Pair(parameters[idx].name, cleanupComment(comment))
        }?.filter { it.second != null } ?: listOf()
        paramComments.forEach { doc.add("\n@param %L %L\n", it.first, it.second) }

        // add any extra comments at the bottom (only used for the pageSize currently)
        extras.forEach { doc.add("\n%L\n", it) }

        // put it all together
        return doc.build()
    }

    // TODO: Samples?
//    private fun createMethodDocSample(ctx: GeneratorContext,
//                                      method: DescriptorProtos.MethodDescriptorProto,
//                                      methodName: String,
//                                      sample: SampleMethod,
//                                      config: FlattenedMethod?): CodeBlock {
//    }

    private fun createCompanion(ctx: GeneratorContext): TypeSpec {
        return TypeSpec.companionObjectBuilder()
                .addKdoc("Utilities for creating a fully configured %N.\n", ctx.className.simpleName())
                .addProperty(PropertySpec.builder(
                        CONST_ALL_SCOPES, ParameterizedTypeName.get(List::class, String::class))
                        .addAnnotation(JvmStatic::class)
                        .initializer("listOf(%L)", ctx.metadata.scopesAsLiteral)
                        .build())
                .addFunctions(createClientFactories(ctx))
                .build()
    }

    // client factory methods for creating client instances via various means
    // (i.e. service accounts, access tokens, etc.)
    private fun createClientFactories(ctx: GeneratorContext): List<FunSpec> {
        val fromAccessToken = FunSpec.builder("fromAccessToken")
                .addKdoc("""
                    |Create a %N with the provided access token.
                    |
                    |TODO: ADD INFO ABOUT REFRESHING
                    |
                    |If a [channel] is not provided one will be created automatically (recommended).
                    |""".trimMargin(), ctx.className.simpleName())
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter("accessToken", GrpcTypes.Auth.AccessToken)
                .addParameter(ParameterSpec.builder("scopes",
                        ParameterizedTypeName.get(List::class, String::class))
                        .defaultValue("%N", CONST_ALL_SCOPES)
                        .build())
                .addParameter(ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable())
                        .defaultValue("%L", "null")
                        .build())
                .returns(ctx.className)
                .addStatement("val credentials = %T.create(accessToken).createScoped(scopes)",
                        GrpcTypes.Auth.GoogleCredentials)
                .addStatement("return %T(channel ?: createChannel(), %T(%T.from(credentials)))",
                        ctx.className, GrpcTypes.Support.ClientCallOptions, GrpcTypes.Auth.MoreCallCredentials)
                .build()

        val fromServiceAccount = FunSpec.builder("fromServiceAccount")
                .addKdoc("""
                    |Create a %N with service account credentials from a JSON [keyFile].
                    |
                    |If a [channel] is not provided one will be created automatically (recommended).
                    |""".trimMargin(), ctx.className.simpleName())
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter("keyFile", InputStream::class)
                .addParameter(ParameterSpec.builder("scopes",
                        ParameterizedTypeName.get(List::class, String::class))
                        .defaultValue("%N", CONST_ALL_SCOPES)
                        .build())
                .addParameter(ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable())
                        .defaultValue("%L", "null")
                        .build())
                .returns(ctx.className)
                .addStatement("val credentials = %T.fromStream(keyFile).createScoped(scopes)",
                        GrpcTypes.Auth.GoogleCredentials)
                .addStatement("return %T(channel ?: createChannel(), %T(%T.from(credentials)))",
                        ctx.className, GrpcTypes.Support.ClientCallOptions, GrpcTypes.Auth.MoreCallCredentials)
                .build()

        val fromCredentials = FunSpec.builder("fromCredentials")
                .addKdoc("""
                    |Create a %N with the provided credentials.
                    |
                    |If a [channel] is not provided one will be created automatically (recommended).
                    |""".trimMargin(), ctx.className.simpleName())
                .addAnnotation(JvmStatic::class)
                .addAnnotation(JvmOverloads::class)
                .addParameter("credentials", GrpcTypes.Auth.GoogleCredentials)
                .addParameter(ParameterSpec.builder(
                        "channel", GrpcTypes.ManagedChannel.asNullable())
                        .defaultValue("%L", "null")
                        .build())
                .returns(ctx.className)
                .addStatement("return %T(channel ?: createChannel(), %T(%T.from(credentials)))",
                        ctx.className, GrpcTypes.Support.ClientCallOptions, GrpcTypes.Auth.MoreCallCredentials)
                .build()

        val createChannel = FunSpec.builder("createChannel")
                .addKdoc("""
                    |Create a [ManagedChannel] to use with %N.
                    |
                    |Prefer to use [fromAccessToken], [fromServiceAccount], or [fromCredentials] unless
                    |you need to customize the channel.
                    |""".trimMargin(), ctx.className.simpleName())
                .addParameter(ParameterSpec.builder("host", String::class)
                        .defaultValue("%S", ctx.metadata.host)
                        .build())
                .addParameter(ParameterSpec.builder("port", Int::class.asTypeName())
                        .defaultValue("443")
                        .build())
                .addParameter(ParameterSpec.builder("enableRetry", Boolean::class.asTypeName())
                        .defaultValue("true")
                        .build())
                .returns(GrpcTypes.ManagedChannel)
                .addStatement("val builder = %T.forAddress(host, port)", GrpcTypes.OkHttpChannelBuilder)
                .beginControlFlow("if (enableRetry)")
                .addStatement("builder.enableRetry()")
                .endControlFlow()
                .addStatement("return builder.build()")
                .build()

        return listOf(fromAccessToken, fromServiceAccount, fromCredentials, createChannel)
    }

    // creates a nested type that will be used to hold the gRPC stubs used by the client
    private fun createStubHolderType(ctx: GeneratorContext): TypeSpec {
        val streamType = GrpcTypes.Support.GrpcClientStub(ctx.typeMap.getKotlinGrpcType(
                ctx.proto, ctx.service, "Grpc.${ctx.service.name}Stub"))
        val futureType = GrpcTypes.Support.GrpcClientStub(ctx.typeMap.getKotlinGrpcType(
                ctx.proto, ctx.service, "Grpc.${ctx.service.name}FutureStub"))
        val opType = GrpcTypes.Support.GrpcClientStub(GrpcTypes.OperationsFutureStub)

        return TypeSpec.classBuilder(STUBS_CLASS_TYPE)
                .addModifiers(KModifier.PRIVATE)
                .primaryConstructor(FunSpec.constructorBuilder()
                        .addParameter(PROP_STUBS_STREAM, streamType)
                        .addParameter(PROP_STUBS_FUTURE, futureType)
                        .addParameter(PROP_STUBS_OPERATION, opType)
                        .build())
                .addProperty(PropertySpec.builder(PROP_STUBS_STREAM, streamType)
                        .initializer(PROP_STUBS_STREAM)
                        .build())
                .addProperty(PropertySpec.builder(PROP_STUBS_FUTURE, futureType)
                        .initializer(PROP_STUBS_FUTURE)
                        .build())
                .addProperty(PropertySpec.builder(PROP_STUBS_OPERATION, opType)
                        .initializer(PROP_STUBS_OPERATION)
                        .build())
                .build()
    }
}
