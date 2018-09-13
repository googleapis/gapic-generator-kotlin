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
import com.google.api.kotlin.generator.grpc.Stubs.Companion.PROP_STUBS_API
import com.google.api.kotlin.generator.grpc.Stubs.Companion.PROP_STUBS_OPERATION
import com.google.api.kotlin.types.GrpcTypes
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * Generates a type that holder the gRPC stubs that will be used by the client.
 *
 * The client will the use [PROP_STUBS_API] and [PROP_STUBS_OPERATION] to make API calls.
 */
internal interface Stubs {
    /** Creates a nested type that will be used to hold the gRPC stubs used by the client */
    fun generateHolderType(ctx: GeneratorContext): TypeSpec

    fun getApiStubType(ctx: GeneratorContext): ParameterizedTypeName

    fun getOperationsStubType(ctx: GeneratorContext): ParameterizedTypeName

    companion object {
        const val PROP_STUBS_API = "api"
        const val PROP_STUBS_OPERATION = "operation"

        const val CLASS_STUBS = "Stubs"
    }
}

internal class StubsImpl(val baseClass: BaseClass) : Stubs {

    override fun generateHolderType(ctx: GeneratorContext): TypeSpec {
        val apiType = getApiStubType(ctx)
        val opType = getOperationsStubType(ctx)

        return TypeSpec.classBuilder(Stubs.CLASS_STUBS)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(Stubs.PROP_STUBS_API, apiType)
                    .addParameter(Stubs.PROP_STUBS_OPERATION, opType)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(Stubs.PROP_STUBS_API, apiType)
                    .initializer(Stubs.PROP_STUBS_API)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(Stubs.PROP_STUBS_OPERATION, opType)
                    .initializer(Stubs.PROP_STUBS_OPERATION)
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder("Factory")
                    .addFunction(
                        FunSpec.builder("create")
                            .addModifiers(KModifier.ABSTRACT)
                            .returns(ClassName("", Stubs.CLASS_STUBS))
                            .addParameter(Properties.PROP_CHANNEL, GrpcTypes.ManagedChannel)
                            .addParameter(
                                Properties.PROP_CALL_OPTS,
                                GrpcTypes.Support.ClientCallOptions
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    override fun getApiStubType(ctx: GeneratorContext) =
        GrpcTypes.Support.GrpcClientStub(baseClass.stubTypeName(ctx))

    override fun getOperationsStubType(ctx: GeneratorContext) =
        GrpcTypes.Support.GrpcClientStub(GrpcTypes.OperationsFutureStub)
}
