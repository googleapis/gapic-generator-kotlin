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
import com.google.api.kotlin.generator.grpc.CompanionObject
import com.google.api.kotlin.generator.grpc.CompanionObjectImpl
import com.google.api.kotlin.generator.grpc.Documentation
import com.google.api.kotlin.generator.grpc.DocumentationImpl
import com.google.api.kotlin.generator.grpc.Functions
import com.google.api.kotlin.generator.grpc.FunctionsImpl
import com.google.api.kotlin.generator.grpc.PROP_CALL_OPTS
import com.google.api.kotlin.generator.grpc.PROP_CHANNEL
import com.google.api.kotlin.generator.grpc.Properties
import com.google.api.kotlin.generator.grpc.PropertiesImpl
import com.google.api.kotlin.generator.grpc.Stubs
import com.google.api.kotlin.generator.grpc.StubsImpl
import com.google.api.kotlin.generator.grpc.UnitTest
import com.google.api.kotlin.generator.grpc.UnitTestImpl
import com.google.api.kotlin.types.GrpcTypes
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Generates a gRPC client by aggregating the results of the sub-generators.
 *
 * @author jbolinger
 */
internal class GRPCGenerator(
    private val stubs: Stubs = StubsImpl(),
    private val properties: Properties = PropertiesImpl(),
    private val companion: CompanionObject = CompanionObjectImpl(),
    private val documentation: Documentation = DocumentationImpl(),
    private val unitTests: UnitTest = UnitTestImpl(stubs),
    private val functions: Functions = FunctionsImpl(stubs, unitTests)
) : AbstractGenerator(), ClientGenerator {

    override fun generateServiceClient(ctx: GeneratorContext): List<GeneratedArtifact> {
        val artifacts = mutableListOf<GeneratedArtifact>()

        val clientType = TypeSpec.classBuilder(ctx.className)
        val apiMethods = functions.generate(ctx)

        // build client
        clientType.addAnnotation(createGeneratedByAnnotation())
        clientType.superclass(GrpcTypes.Support.GrpcClient)
        clientType.addSuperclassConstructorParameter("%N", PROP_CHANNEL)
        clientType.addSuperclassConstructorParameter("%N", PROP_CALL_OPTS)
        clientType.addKdoc(documentation.generateClassDoc(ctx))
        clientType.primaryConstructor(properties.generatePrimaryConstructor())
        clientType.addProperties(properties.generate(ctx))
        clientType.addFunctions(apiMethods.map { it.function })
        clientType.addType(companion.generate(ctx))
        clientType.addType(stubs.generateHolderType(ctx))

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
        unitTests.generate(ctx, apiMethods)?.let {
            artifacts.add(it)
        }

        // all done!
        return artifacts.toList()
    }
}
