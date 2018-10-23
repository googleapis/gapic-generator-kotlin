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
import com.google.api.kotlin.types.GrpcTypes
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import java.io.File
import java.io.InputStream

/**
 * Generates a companion object for the client, which is responsible for
 * creating instances of the client (i.e. a factory).
 */
internal interface CompanionObject {
    fun generate(context: GeneratorContext): TypeSpec

    companion object {
        const val VAL_ALL_SCOPES = "ALL_SCOPES"
        const val VAL_RETRY = "RETRY"
    }
}

internal class CompanionObjectImpl : CompanionObject {

    override fun generate(context: GeneratorContext): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .addKdoc(
                "Utilities for creating a fully configured %N.\n",
                context.className.simpleName
            )
            .addProperty(
                PropertySpec.builder(
                    CompanionObject.VAL_ALL_SCOPES,
                    List::class.parameterizedBy(String::class)
                )
                    .addKdoc("Default scopes to use.\n")
                    .addAnnotation(JvmStatic::class)
                    .initializer("listOf(%L)", context.serviceOptions.scopes.joinToString(", ") { "\"$it\"" })
                    .build()
            )
            .addProperty(createDefaultRetries(context))
            .addFunctions(createClientFactories(context))
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
            .addCode(
                """
                |val credentials = %T.create(accessToken).createScoped(scopes)
                |return %T(
                |    channel ?: createChannel(),
                |    %T(credentials = %T.from(credentials), retry = %L)
                |)
                |""".trimMargin(),
                GrpcTypes.Auth.GoogleCredentials,
                ctx.className,
                GrpcTypes.Support.ClientCallOptions,
                GrpcTypes.Auth.MoreCallCredentials,
                CompanionObject.VAL_RETRY
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
            .addCode(
                """
                |val credentials = %T.fromStream(keyFile).createScoped(scopes)
                |return %T(
                |    channel ?: createChannel(),
                |    %T(credentials = %T.from(credentials), retry = %L)
                |)
                |""".trimMargin(),
                GrpcTypes.Auth.GoogleCredentials,
                ctx.className,
                GrpcTypes.Support.ClientCallOptions,
                GrpcTypes.Auth.MoreCallCredentials,
                CompanionObject.VAL_RETRY
            )
            .build()

        val fromEnv = FunSpec.builder("fromEnvironment")
            .addKdoc(
                """
                |Create a %N from the current system's environment.
                |
                |Currently, this method only supports service account credentials that are read from the
                |path defined by the environment [variableName], which is `CREDENTIALS` by default.
                |
                |If a [channel] is not provided one will be created automatically (recommended).
                |""".trimMargin(), ctx.className.simpleName
            )
            .addAnnotation(JvmStatic::class)
            .addAnnotation(JvmOverloads::class)
            .addParameter(
                ParameterSpec.builder("variableName", String::class)
                    .defaultValue("%S", "CREDENTIALS")
                    .build()
            )
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
            .addCode(
                """
                |val path = System.getenv(variableName) ?: throw %T("Credentials environment variable is not set: ${'$'}variableName")
                |return %T(path).inputStream().use {
                |    %T.fromServiceAccount(it, scopes, channel)
                |}
                |""".trimMargin(),
                IllegalStateException::class.asTypeName(),
                File::class.asTypeName(),
                ctx.className
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
            .addParameter(
                ParameterSpec.builder(
                    "credentials", GrpcTypes.Auth.GoogleCredentials.asNullable()
                )
                    .defaultValue("null")
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
            .addCode(
                """
                |val cred = credentials?.let { %T.from(it) }
                |return %T(
                |    channel ?: createChannel(),
                |    %T(credentials = cred, retry = %L)
                |)
                |""".trimMargin(),
                GrpcTypes.Auth.MoreCallCredentials,
                ctx.className,
                GrpcTypes.Support.ClientCallOptions,
                CompanionObject.VAL_RETRY
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
            .addCode(
                """
                |return %T(
                |    channel ?: createChannel(),
                |    options ?: %T(),
                |    factory
                |)
                |""".trimMargin(),
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
                    .defaultValue("%S", ctx.serviceOptions.host)
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
                GrpcTypes.ManagedChannelBuilder
            )
            .beginControlFlow("if (enableRetry)")
            .addStatement("builder.enableRetry()")
            .endControlFlow()
            .addStatement("return builder.build()")
            .build()

        return listOf(
            fromCredentials,
            fromEnv,
            fromServiceAccount,
            fromAccessToken,
            fromStubs,
            createChannel
        )
    }

    // creates a set of default options
    private fun createDefaultRetries(context: GeneratorContext): PropertySpec {
        val prop = PropertySpec.builder(
            CompanionObject.VAL_RETRY, GrpcTypes.Support.Retry
        )
            .addAnnotation(JvmStatic::class)
            .addKdoc("Default operations to retry.\n")

        // create a map of function name to the retry codes
        val retryEntries = context.serviceOptions.methods
            .filter { it.retry != null && it.retry.codes.isNotEmpty() }
            .map { method ->
                val codes = method.retry!!.codes.joinToString(", ") { "Status.Code.${it.name}" }
                CodeBlock.of("%S to setOf(%L)", method.name.decapitalize(), codes)
            }
            .toTypedArray()

        if (retryEntries.isNotEmpty()) {
            prop.initializer(
                """
                |%T(mapOf(
                |    ${retryEntries.joinToString(",\n    ") { "%L" }}
                |))
                """.trimMargin(),
                GrpcTypes.Support.GrpcBasicRetry,
                *retryEntries
            )
        } else {
            prop.initializer("%T(mapOf())", GrpcTypes.Support.GrpcBasicRetry)
        }

        return prop.build()
    }
}
