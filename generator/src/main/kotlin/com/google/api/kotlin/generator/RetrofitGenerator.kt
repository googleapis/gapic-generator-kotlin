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
import com.google.api.kotlin.types.GrpcTypes
import com.google.api.kotlin.types.RetrofitTypes
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import mu.KotlinLogging
import java.io.InputStream

private val log = KotlinLogging.logger {}

// properties, params, etc. for generated client
private const val PROP_CLIENT = "client"
private const val PARAM_REQUEST = "request"
private const val RETROFIT_INTERFACE_NAME = "RPC"

/**
 * Generates a gRPC-fallback client via Retrofit (currently incomplete w/ a basic implementation)
 *
 * @author jbolinger
 */
internal class RetrofitGenerator : AbstractGenerator(), ClientGenerator {

    override fun generateServiceClient(ctx: GeneratorContext): List<GeneratedArtifact> {
        val type = TypeSpec.classBuilder(ctx.className)

        // build client
        type.addAnnotation(createGeneratedByAnnotation())
        type.primaryConstructor(createPrimaryConstructor(ctx))
        type.addProperties(createParameters(ctx))
        type.addFunctions(createMethods(ctx))
        type.addType(createRetrofitType(ctx))
        type.addType(
            TypeSpec.companionObjectBuilder()
                .addFunctions(createClientFactories(ctx))
                .build()
        )

        // all done!
        return listOf(
            GeneratedSource(
                ctx.className.packageName,
                ctx.className.simpleName,
                types = listOf(type.build())
            )
        )
    }

    /** constructor for the client  */
    private fun createPrimaryConstructor(ctx: GeneratorContext): FunSpec {
        return FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter(
                PROP_CLIENT, ClassName("",
                    RETROFIT_INTERFACE_NAME
                ))
            .build()
    }

    /** parameters for the client */
    private fun createParameters(ctx: GeneratorContext): List<PropertySpec> {
        val client = PropertySpec.builder(
            PROP_CLIENT,
            ClassName("", RETROFIT_INTERFACE_NAME)
        )
            .initializer(PROP_CLIENT)
            .build()

        return listOf(client)
    }

    /** api methods */
    private fun createMethods(ctx: GeneratorContext): List<FunSpec> {
        return ctx.service.methodList.flatMap {
            log.debug { "processing proto method: ${it.name}" }
            when {
                it.hasClientStreaming() || it.hasServerStreaming() -> createStreamingMethods(
                    it,
                    ctx
                )
                else -> createUnaryMethods(it, ctx)
            }
        }
    }

    private fun createUnaryMethods(
        method: DescriptorProtos.MethodDescriptorProto,
        ctx: GeneratorContext
    ): List<FunSpec> {
        // unchanged method
        val methodName = method.name.decapitalize()
        val normal = FunSpec.builder(methodName)
            .addParameter(PARAM_REQUEST, ctx.typeMap.getKotlinType(method.inputType))
            .returns(RetrofitTypes.Call.parameterizedBy(ctx.typeMap.getKotlinType(method.outputType)))
            .addStatement("return %N.%L(%N)",
                PROP_CLIENT, methodName,
                PARAM_REQUEST
            )
            .build()

        return listOf(normal)
    }

    private fun createStreamingMethods(
        method: DescriptorProtos.MethodDescriptorProto,
        ctx: GeneratorContext
    ): List<FunSpec> {
        log.debug { "skipping streaming method: ${method.name} (not supported by transport)" }
        return listOf()
    }

    /** retrofit interface for the api */
    private fun createRetrofitType(ctx: GeneratorContext): TypeSpec {
        return TypeSpec.interfaceBuilder(RETROFIT_INTERFACE_NAME)
            .addFunctions(
                ctx.service.methodList
                    .filter { !it.hasClientStreaming() }
                    .filter { !it.hasServerStreaming() }
                    .map {
                        FunSpec.builder(it.name.decapitalize())
                            .addModifiers(KModifier.ABSTRACT)
                            .addParameter(
                                ParameterSpec.builder(
                                    PARAM_REQUEST, ctx.typeMap.getKotlinType(it.inputType)
                                )
                                    .addAnnotation(RetrofitTypes.Body)
                                    .build()
                            )
                            .returns(RetrofitTypes.Call.parameterizedBy(ctx.typeMap.getKotlinType(it.outputType)))
                            .addAnnotation(
                                AnnotationSpec.builder(RetrofitTypes.POST)
                                    .addMember("\"/\\\$rpc/${ctx.proto.`package`}.${ctx.service.name}/${it.name}\"")
                                    .build()
                            )
                            .build()
                    }).build()
    }

    /** client factory */
    private fun createClientFactories(ctx: GeneratorContext): List<FunSpec> {
        val serviceAccount = FunSpec.builder("fromServiceAccount")
            .addAnnotation(JvmStatic::class)
            .addAnnotation(JvmOverloads::class)
            .addParameter("keyFile", InputStream::class)
            .addParameter(
                ParameterSpec.builder("host", String::class)
                    .defaultValue("%S", "https://${ctx.metadata.host}")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "scopes",
                    List::class.parameterizedBy(String::class)
                )
                    .defaultValue("listOf(%L)", ctx.metadata.scopesAsLiteral)
                    .build()
            )
            .returns(ctx.className)
            .addStatement(
                "return %T(%T.createClient(keyFile, host, scopes))",
                ctx.className, ctx.className
            )
            .build()

        val main = FunSpec.builder("createClient")
            .addAnnotation(JvmStatic::class)
            .addParameter("keyFile", InputStream::class)
            .addParameter("host", String::class)
            .addParameter("scopes", List::class.parameterizedBy(String::class))
            .returns(ClassName("", RETROFIT_INTERFACE_NAME))
            .addStatement("val keyContent = keyFile.readBytes()")
            .addStatement(
                "val httpClient = %T.Builder().addInterceptor { chain ->",
                RetrofitTypes.OkHttpClient
            )
            .addStatement(
                "  val headers = %T.fromStream(keyContent.inputStream()).createScoped(scopes).getRequestMetadata()",
                GrpcTypes.Auth.GoogleCredentials
            )
            .addStatement("  val r = chain.request().newBuilder()")
            .addStatement("  headers.entries.forEach { e -> headers.get(e.key)?.forEach { r.addHeader(e.key, it) } }")
            .addStatement("  chain.proceed(r.build())")
            .addStatement("}.build()")
            .addStatement(
                "val retrofit = %T.Builder().baseUrl(host).addConverterFactory(%T.create()).client(httpClient).build()",
                RetrofitTypes.Retrofit,
                RetrofitTypes.ProtoConverterFactory
            )
            .addStatement("return retrofit.create(%N::class.java)",
                RETROFIT_INTERFACE_NAME
            )
            .build()

        return listOf(serviceAccount, main)
    }
}
