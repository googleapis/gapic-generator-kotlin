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
import com.google.api.kotlin.generator.AbstractGenerator
import com.google.api.kotlin.types.GrpcTypes
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.InputStream

/**
 * Generates a companion object for the client, which is responsible for
 * creating instances of the client (i.e. a factory).
 */
internal interface CompanionObject {
    fun generate(ctx: GeneratorContext): TypeSpec

    companion object {
        const val VAL_ALL_SCOPES = "ALL_SCOPES"
    }
}

internal class CompanionObjectImpl : AbstractGenerator(), CompanionObject {

    override fun generate(ctx: GeneratorContext): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .addKdoc(
                "Utilities for creating a fully configured %N.\n",
                ctx.className.simpleName
            )
            .addProperty(
                PropertySpec.builder(
                    CompanionObject.VAL_ALL_SCOPES, List::class.parameterizedBy(String::class)
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
                    .defaultValue("%N", CompanionObject.VAL_ALL_SCOPES)
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
                    .defaultValue("%N", CompanionObject.VAL_ALL_SCOPES)
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
            .addParameter(
                "factory", ClassName("", Stubs.CLASS_STUBS, "Factory")
            )
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
                "return %T(channel ?: createChannel(), options ?: %T(), factory)",
                ctx.className,
                GrpcTypes.Support.ClientCallOptions
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
