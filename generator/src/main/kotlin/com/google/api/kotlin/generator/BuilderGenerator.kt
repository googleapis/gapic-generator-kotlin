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
import com.google.api.kotlin.types.GrpcTypes
import com.google.api.kotlin.util.FieldNamer
import com.google.api.kotlin.util.asClassName
import com.google.api.kotlin.util.isMap
import com.google.api.kotlin.util.isMessageType
import com.google.api.kotlin.util.isRepeated
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
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
        types.getAllTypes()
            .asSequence()
            .map { TypeInfo(types.getProtoTypeDescriptor(it.protoName), ClassName.bestGuess(it.kotlinName)) }
            .filter {
                !(it.className.packageName == "com.google.protobuf" &&
                    (it.className.canonicalName.contains("DescriptorProtos") || SKIP.contains(it.className.simpleName)))
            }
            .filter { !it.proto.isMap() }
            .forEach { type ->
                val builderType = ClassName.bestGuess("${type.className}.Builder")

                // construct function name
                var parentType = type.className.enclosingClassName()
                val parentTypes = mutableListOf<String>()
                while (parentType != null) {
                    parentTypes.add(parentType.simpleName)
                    parentType = parentType.enclosingClassName()
                }

                // create builder function body
                val builder = FunSpec.builder(type.className.simpleName)
                    .returns(type.className)
                    .addParameter(
                        "init",
                        LambdaTypeName.get(
                            builderType.annotated(AnnotationSpec.builder(GrpcTypes.Support.ProtoBuilder).build()),
                            listOf(),
                            Unit::class.asTypeName()
                        )
                    )
                    .addStatement("return %T.newBuilder().apply(init).build()", type.className)
                if (parentTypes.isNotEmpty()) {
                    builder.receiver(
                        ClassName(
                            type.className.packageName,
                            parentTypes.reversed().joinToString(".")
                        )
                    )
                }

                // if this type has any repeated fields create another function for them
                val repeatedSetters = type.proto.fieldList
                    .asSequence()
                    .filter { it.isRepeated() && !it.isMap(types) }
                    .map {
                        val fieldType = it.asClassName(types)
                        val funName = FieldNamer.getFieldName(it.name)
                        if (it.isMessageType()) {
                            val fieldBuilderType = ClassName.bestGuess("$fieldType.Builder")
                                .annotated(AnnotationSpec.builder(GrpcTypes.Support.ProtoBuilder).build())
                            FunSpec.builder(funName)
                                .receiver(builderType)
                                .addParameter(
                                    "init",
                                    LambdaTypeName.get(fieldBuilderType, listOf(), Unit::class.asTypeName()),
                                    KModifier.VARARG
                                )
                                .addStatement(
                                    "this.%L(init.map { %T.newBuilder().apply(it).build() })",
                                    FieldNamer.getSetterRepeatedName(it.name),
                                    fieldType
                                )
                                .build()
                        } else {
                            FunSpec.builder(funName)
                                .receiver(builderType)
                                .addParameter("items", fieldType, KModifier.VARARG)
                                .addStatement(
                                    "this.%L(items.asList())",
                                    FieldNamer.getSetterRepeatedName(it.name)
                                )
                                .build()
                        }
                    }

                // get list of builder functions in this package and append to it
                var builders = packagesToBuilders[type.className.packageName]
                if (builders == null) {
                    builders = mutableListOf()
                    packagesToBuilders[type.className.packageName] = builders
                }
                builders.add(builder.build())
                builders.addAll(repeatedSetters)
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

private data class TypeInfo(val proto: DescriptorProtos.DescriptorProto, val className: ClassName)
