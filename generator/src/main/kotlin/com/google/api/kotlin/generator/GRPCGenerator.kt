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
import com.google.api.kotlin.generator.grpc.Properties
import com.google.api.kotlin.generator.grpc.PropertiesImpl
import com.google.api.kotlin.generator.grpc.Stubs
import com.google.api.kotlin.generator.grpc.StubsImpl
import com.google.api.kotlin.generator.grpc.UnitTest
import com.google.api.kotlin.generator.grpc.UnitTestImpl
import com.google.api.kotlin.types.GrpcTypes
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import javax.annotation.Generated

/**
 * Generates a gRPC client by aggregating the results of the sub-generators.
 *
 * @author jbolinger
 */
internal class GRPCGenerator(
    private val stubs: Stubs = StubsImpl(),
    private val properties: Properties = PropertiesImpl(stubs),
    private val companion: CompanionObject = CompanionObjectImpl(),
    private val documentation: Documentation = DocumentationImpl(),
    private val unitTests: UnitTest = UnitTestImpl(stubs),
    private val functions: Functions = FunctionsImpl(documentation, unitTests)
) : ClientGenerator {

    override fun generateServiceClient(context: GeneratorContext): List<GeneratedArtifact> {
        val artifacts = mutableListOf<GeneratedArtifact>()

        // hallmark
        val byAnnotation = AnnotationSpec.builder(Generated::class)
            .addMember("%S", this::class.qualifiedName!!)
            .build()

        // build stub class for client
        val stub = stubs.generate(context, byAnnotation)

        // build client type
        val clientType = TypeSpec.classBuilder(context.className)
        val clientImports = mutableListOf<ClassName>()

        // build client implementation
        clientType.addAnnotation(byAnnotation)
        clientType.addKdoc(documentation.generateClassKDoc(context))

        val clientApiMethods = functions.generate(context)
        clientType.primaryConstructor(properties.generatePrimaryConstructor())
        clientType.addProperties(properties.generate(context))
        clientType.addFunctions(clientApiMethods.map { it.function })
        clientType.addType(companion.generate(context))
        clientType.addType(stubs.generateHolderType(context))

        // add statics
        clientImports += listOf("prepare", "pager")
            .map { ClassName(GrpcTypes.Support.SUPPORT_LIB_GRPC_PACKAGE, it) }
        clientImports += listOf("coroutineScope", "async")
            .map { ClassName("kotlinx.coroutines", it) }

        // add client type
        artifacts.add(
            GeneratedSource(
                context.className.packageName,
                context.className.simpleName,
                types = listOf(clientType.build()),
                imports = clientImports
            )
        )
        artifacts.add(stub)

        // build unit tests
        unitTests.generate(context, clientApiMethods)?.let {
            artifacts.add(it)
        }

        // all done!
        return artifacts.toList()
    }
}
