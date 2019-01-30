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
                    .addKdoc("Default scopes to use. Use [prepare] to override as needed.\n")
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
    private fun createClientFactories(context: GeneratorContext): List<FunSpec> {
        val preferredMethodText = if (context.metadata.authentication.hasGoogleCloud) {
            "\n\nThis is an advanced method. Prefer using [fromAccessToken], [fromServiceAccount], or [fromCredentials]."
        } else {
            ""
        }

        val create = FunSpec.builder("create")
            .addKdoc(
                """
                |Create a %N with the provided [channel], [options], or stub [factory].%L
                |""".trimMargin(),
                context.className.simpleName,
                preferredMethodText
            )
            .addAnnotation(JvmStatic::class)
            .addAnnotation(JvmOverloads::class)
            .addParameter(
                ParameterSpec.builder(
                    "channel", GrpcTypes.ManagedChannel.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "options", GrpcTypes.Support.ClientCallOptions.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "factory", ClassName("", Stubs.CLASS_STUBS, "Factory").copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .returns(context.className)
            .addCode(
                """
                |return %T(
                |    channel ?: createChannel(),
                |    options ?: %T(),
                |    factory
                |)
                |""".trimMargin(),
                context.className,
                GrpcTypes.Support.ClientCallOptions
            )
            .build()

        val createChannel = FunSpec.builder("createChannel")
            .addKdoc(
                """
                |Create a [ManagedChannel] to use with a %N.
                |
                |[enableRetry] can be used to enable server managed retries, which is currently
                |experimental. You should not use any client retry settings if you enable it.%L
                |""".trimMargin(),
                context.className.simpleName,
                preferredMethodText
            )
            .addAnnotation(JvmStatic::class)
            .addAnnotation(JvmOverloads::class)
            .addParameter(
                ParameterSpec.builder("host", String::class)
                    .defaultValue("%S", context.serviceOptions.host)
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("port", Int::class)
                    .defaultValue("443")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("enableRetry", Boolean::class)
                    .defaultValue("false")
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

        val list = mutableListOf<FunSpec>()

        // add any additional auth methods
        if (context.metadata.authentication.hasGoogleCloud) {
            list += createGoogleCloudClientFactories(context)
        }

        return list + listOf(create, createChannel)
    }

    private fun createGoogleCloudClientFactories(context: GeneratorContext): List<FunSpec> {
        val fromEnv = FunSpec.builder("fromEnvironment")
            .addKdoc(
                """
                |Create a %N from the current system's environment.
                |
                |Currently, this method only supports service account credentials that are read from the
                |path defined by the environment [variableName], which is `GOOGLE_APPLICATION_CREDENTIALS` by default.
                |
                |If a [channel] is not provided one will be created automatically (recommended).
                |""".trimMargin(), context.className.simpleName
            )
            .addAnnotation(JvmStatic::class)
            .addAnnotation(JvmOverloads::class)
            .addParameter(
                ParameterSpec.builder("variableName", String::class)
                    .defaultValue("%S", "GOOGLE_APPLICATION_CREDENTIALS")
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
                    "channel", GrpcTypes.ManagedChannel.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .returns(context.className)
            .addCode(
                """
                |val path = System.getenv(variableName) ?: throw·%T("Credentials·environment·variable·is·not·set:·${'$'}variableName")
                |return %T(path).inputStream().use·{
                |    %T.fromServiceAccount(it, scopes, channel)
                |}
                |""".trimMargin(),
                IllegalStateException::class.asTypeName(),
                File::class.asTypeName(),
                context.className
            )
            .build()

        val fromAccessToken = FunSpec.builder("fromAccessToken")
            .addKdoc(
                """
                |Create a %N with the provided [accessToken].
                |
                |If a [channel] is not provided one will be created automatically (recommended).
                |""".trimMargin(), context.className.simpleName
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
                    "channel", GrpcTypes.ManagedChannel.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .returns(context.className)
            .addCode(
                """
                |val credentials = %T.create(accessToken).createScoped(scopes)
                |return %T(
                |    channel ?: createChannel(),
                |    %T(credentials = %T.from(credentials), retry = %L)
                |)
                |""".trimMargin(),
                GrpcTypes.Auth.GoogleCredentials,
                context.className,
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
                |""".trimMargin(), context.className.simpleName
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
                    "channel", GrpcTypes.ManagedChannel.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .returns(context.className)
            .addCode(
                """
                |val credentials = %T.fromStream(keyFile).createScoped(scopes)
                |return %T(
                |    channel ?: createChannel(),
                |    %T(credentials = %T.from(credentials), retry = %L)
                |)
                |""".trimMargin(),
                GrpcTypes.Auth.GoogleCredentials,
                context.className,
                GrpcTypes.Support.ClientCallOptions,
                GrpcTypes.Auth.MoreCallCredentials,
                CompanionObject.VAL_RETRY
            )
            .build()

        val fromCredentials = FunSpec.builder("fromCredentials")
            .addKdoc(
                """
                |Create a %N with the provided [credentials].
                |
                |If a [channel] is not provided one will be created automatically (recommended).
                |""".trimMargin(), context.className.simpleName
            )
            .addAnnotation(JvmStatic::class)
            .addAnnotation(JvmOverloads::class)
            .addParameter(
                ParameterSpec.builder(
                    "credentials", GrpcTypes.Auth.GoogleCredentials.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .addParameter(
                ParameterSpec.builder(
                    "channel", GrpcTypes.ManagedChannel.copy(nullable = true)
                )
                    .defaultValue("null")
                    .build()
            )
            .returns(context.className)
            .addCode(
                """
                |val cred = credentials?.let·{ %T.from(it) }
                |return %T(
                |    channel ?: createChannel(),
                |    %T(credentials = cred, retry = %L)
                |)
                |""".trimMargin(),
                GrpcTypes.Auth.MoreCallCredentials,
                context.className,
                GrpcTypes.Support.ClientCallOptions,
                CompanionObject.VAL_RETRY
            )
            .build()

        return listOf(
            fromEnv,
            fromCredentials,
            fromServiceAccount,
            fromAccessToken
        )
    }

    // creates a set of default options
    private fun createDefaultRetries(context: GeneratorContext): PropertySpec {
        val prop = PropertySpec.builder(
            CompanionObject.VAL_RETRY, GrpcTypes.Support.Retry
        )
            .addAnnotation(JvmStatic::class)
            .addKdoc(
                """
                |Default operations to retry on failure. Use [prepare] to override as needed.
                |
                |Note: This setting controls client side retries. If you enable
                |server managed retries on the channel do not use this.
                |""".trimMargin()
            )

        // create a map of function name to the retry codes
        val retryEntries = context.serviceOptions.methods
            .filter { it.retry != null && it.retry.codes.isNotEmpty() }
            .map { method ->
                val codes = method.retry!!.codes
                CodeBlock.of(
                    "%S to setOf(${codes.joinToString(", ") { "%T.${it.name}" }})",
                    method.name.decapitalize(),
                    *codes.map { GrpcTypes.StatusCode }.toTypedArray()
                )
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
