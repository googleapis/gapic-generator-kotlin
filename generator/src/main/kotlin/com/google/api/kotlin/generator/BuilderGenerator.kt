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
import com.google.api.kotlin.util.isRepeated
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName

/**
 * Generates an alternative builder for message types to replace the
 * Java builder pattern.
 */
internal class BuilderGenerator {

    private val SKIP = listOf("Any", "Empty")

    fun generate(types: ProtobufTypeMapper): List<GeneratedSource> {
        // package name -> builder functions
        val packagesToBuilders = PackagesToBuilders()

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
                        val listType = List::class.asClassName().parameterizedBy(fieldType)
                        val propertyName = FieldNamer.getFieldName(it.name)

                        PropertySpec.builder(propertyName, listType)
                            .receiver(builderType)
                            .mutable(true)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return this.%L", FieldNamer.getAccessorRepeatedName(it.name))
                                    .build()
                            )
                            .setter(
                                FunSpec.setterBuilder()
                                    .addParameter("values", listType)
                                    .addStatement("this.%L(values)", FieldNamer.getSetterRepeatedName(it.name))
                                    .build()
                            )
                            .build()
                    }

                // get list of builder functions in this package and append to it
                var (funBuilders, propBuilders) = packagesToBuilders[type.className.packageName]
                funBuilders.add(builder.build())
                propBuilders.addAll(repeatedSetters)
            }

        // collect the builder functions into types
        return packagesToBuilders.map { packageName, functions, properties ->
            GeneratedSource(
                packageName,
                "KotlinBuilders",
                functions = functions,
                properties = properties
            )
        }
    }
}

private data class TypeInfo(val proto: DescriptorProtos.DescriptorProto, val className: ClassName)

private typealias FunList = MutableList<FunSpec>
private typealias PropList = MutableList<PropertySpec>
private typealias BuilderIterator<T> = (packageName: String, functions: List<FunSpec>, properties: List<PropertySpec>) -> T

private data class PackagesToBuilders(
    private val functions: MutableMap<String, FunList> = mutableMapOf(),
    private val properties: MutableMap<String, PropList> = mutableMapOf()
) {

    fun <T> map(handler: BuilderIterator<T>) = functions.keys.map {
        handler(it, functions[it]!!, properties[it]!!)
    }

    operator fun get(key: String): Pair<FunList, PropList> {
        if (!functions.containsKey(key)) {
            functions[key] = mutableListOf()
        }
        if (!properties.containsKey(key)) {
            properties[key] = mutableListOf()
        }
        return Pair(functions[key]!!, properties[key]!!)
    }
}