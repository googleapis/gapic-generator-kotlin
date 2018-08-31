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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec

/**
 * Generates the properties and constructors for the client.
 */
internal interface Properties {
    fun generate(ctx: GeneratorContext): List<PropertySpec>
    fun generatePrimaryConstructor(): FunSpec

    companion object {
        const val PROP_CHANNEL = "channel"
        const val PROP_CALL_OPTS = "options"
        const val PROP_STUBS = "stubs"

        const val PARAM_FACTORY = "factory"
    }
}

internal class PropertiesImpl : AbstractGenerator(), Properties {

    override fun generate(ctx: GeneratorContext): List<PropertySpec> {
        val stub = PropertySpec.builder(
            Properties.PROP_STUBS, ClassName.bestGuess(Stubs.CLASS_STUBS)
        )
            .addModifiers(KModifier.PRIVATE)
            .initializer(
                CodeBlock.builder()
                    .add(
                        "%N?.create(%N, %N) ?: %T(\n",
                        Properties.PARAM_FACTORY,
                        Properties.PROP_CHANNEL,
                        Properties.PROP_CALL_OPTS,
                        ClassName.bestGuess(Stubs.CLASS_STUBS)
                    )
                    .add(
                        "Stub(%N).prepare(%N),\n",
                        Properties.PROP_CHANNEL,
                        Properties.PROP_CALL_OPTS
                    )
                    .add(
                        "%T.newFutureStub(%N).prepare(%N))",
                        GrpcTypes.OperationsGrpc,
                        Properties.PROP_CHANNEL,
                        Properties.PROP_CALL_OPTS
                    )
                    .build()
            )
            .build()

        return listOf(stub)
    }

    override fun generatePrimaryConstructor(): FunSpec {
        return FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)
            .addParameter(Properties.PROP_CHANNEL, GrpcTypes.ManagedChannel)
            .addParameter(Properties.PROP_CALL_OPTS, GrpcTypes.Support.ClientCallOptions)
            .addParameter(
                ParameterSpec.builder(
                    Properties.PARAM_FACTORY,
                    ClassName("", Stubs.CLASS_STUBS, "Factory").asNullable()
                ).defaultValue("null").build()
            ).build()
    }
}
