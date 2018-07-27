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

import com.google.api.kotlin.GeneratedSource
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.asTypeName

/**
 * Generates an alternative builder for message types to replace the
 * Java builder pattern.
 */
internal class BuilderGenerator {

    private val SKIP = listOf("Any", "Empty")

    fun generate(types: ProtobufTypeMapper): List<GeneratedSource> {
        // package name -> builder functions
        val packagesToBuilders = mutableMapOf<String, MutableList<FunSpec>>()

        // generate all the builder functions
        types.getAllKotlinTypes()
            .map { ClassName.bestGuess(it) }
            .filter {
                !(it.packageName == "com.google.protobuf" &&
                    (it.canonicalName.contains("DescriptorProtos") || SKIP.contains(it.simpleName)))
            }
            .forEach { type ->
                val builderType = ClassName.bestGuess("$type.Builder")

                // construct function name
                var parentType = type.enclosingClassName()
                var parentTypes = mutableListOf<String>()
                while (parentType != null) {
                    parentTypes.add(parentType.simpleName)
                    parentType = parentType.enclosingClassName()
                }

                // create builder function body
                val builder = FunSpec.builder(type.simpleName)
                    .returns(type)
                    .addParameter(
                        "init",
                        LambdaTypeName.get(builderType, listOf(), Unit::class.asTypeName())
                    )
                    .addStatement("return %T.newBuilder().apply(init).build()", type)
                if (parentTypes.isNotEmpty()) {
                    builder.receiver(
                        ClassName(
                            type.packageName,
                            parentTypes.reversed().joinToString(".")
                        )
                    )
                }

                // get list of builder functions in this package and append to it
                var builders = packagesToBuilders[type.packageName]
                if (builders == null) {
                    builders = mutableListOf()
                    packagesToBuilders[type.packageName] = builders
                }
                builders.add(builder.build())
            }

        // collect the builder functions into types
        return packagesToBuilders.keys.map { packageName ->
            GeneratedSource(
                packageName,
                "KotlinBuilders",
                functions = packagesToBuilders[packageName]?.toList()
                    ?: throw IllegalStateException("No functions in package!/**/")
            )
        }
    }
}