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

import com.google.api.kotlin.BuilderGenerator
import com.google.api.kotlin.GeneratedSource
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.types.GrpcTypes
import com.google.api.kotlin.util.FieldNamer
import com.google.api.kotlin.util.asClassName
import com.google.api.kotlin.util.describeMap
import com.google.api.kotlin.util.isMap
import com.google.api.kotlin.util.isRepeated
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName

private val SKIP = listOf("Any", "Empty")

/**
 * Generates an alternative builder for message types to replace the
 * Java builder pattern.
 */
internal class DSLBuilderGenerator : BuilderGenerator {

    override fun generate(types: ProtobufTypeMapper): List<GeneratedSource> {
        // package name -> builder functions
        val packagesToBuilders = PackagesToBuilders()

        // generate all the builder functions
        types.getAllTypes()
            .map { TypeInfo(types.getProtoTypeDescriptor(it.protoName), ClassName.bestGuess(it.kotlinName)) }
            .filter {
                !(it.className.packageName == "com.google.protobuf" &&
                    (it.className.canonicalName.contains("DescriptorProtos") || SKIP.contains(it.className.simpleName)))
            }
            .filter { !it.proto.isMap() }
            .forEach { addBuildersForType(packagesToBuilders, types, it) }

        return packagesToBuilders.asGeneratedSources()
    }

    // adds builders for the given to the set of package-level builders
    private fun addBuildersForType(builders: PackagesToBuilders, types: ProtobufTypeMapper, type: TypeInfo) {
        val builderType = ClassName.bestGuess("${type.className}.Builder")

        // Create the wrapper type
        val wrapperClass = type.className.peerClass("${type.className.simpleName}Dsl")
        val wrapperBuilder = TypeSpec.classBuilder(wrapperClass)
            .addModifiers(KModifier.INLINE)
            .addAnnotation(GrpcTypes.Support.ProtoBuilder)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("builder", builderType)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("builder", builderType)
                    .initializer("builder")
                    .build()
            )

        // create a property for each normal field
        wrapperBuilder.addProperties(type.mapNormalFields(types) {
            val fieldType = it.asClassName(types)
            val propertyName = FieldNamer.getFieldName(it.name)
            val accessor = FieldNamer.getAccessorName(it.name)

            PropertySpec.builder(propertyName, fieldType)
                .mutable(true)
                .getter(
                    FunSpec.getterBuilder()
                        .addModifiers(KModifier.INLINE)
                        .addStatement("return builder.%L", accessor)
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addModifiers(KModifier.INLINE)
                        .addParameter("value", fieldType)
                        .addStatement("builder.%L = value", accessor)
                        .build()
                ).build()
        })

        // create an extensions property for repeated fields
        wrapperBuilder.addProperties(type.mapRepeatedFields(types) {
            val fieldType = it.asClassName(types)
            val propertyName = FieldNamer.getFieldName(it.name)
            val listType = List::class.asTypeName().parameterizedBy(fieldType)

            PropertySpec.builder(propertyName, listType)
                .mutable(true)
                .getter(
                    FunSpec.getterBuilder()
                        .addModifiers(KModifier.INLINE)
                        .addStatement("return builder.%L", FieldNamer.getAccessorRepeatedName(it.name))
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addModifiers(KModifier.INLINE)
                        .addParameter("values", listType)
                        .addCode(
                            """
                            |builder.clear%L()
                            |builder.%L(values)
                            |""".trimMargin(),
                            propertyName.capitalize(),
                            FieldNamer.getSetterRepeatedName(it.name)
                        ).build()
                ).build()
        })

        // create an extensions property for repeated fields
        wrapperBuilder.addFunctions(type.mapRepeatedFields(types) {
            val fieldType = it.asClassName(types)
            val propertyName = FieldNamer.getFieldName(it.name)

            FunSpec.builder(propertyName)
                .addModifiers(KModifier.INLINE)
                .addParameter(
                    ParameterSpec.builder("values", fieldType, KModifier.VARARG)
                        .build()
                )
                .addStatement("builder.%L(values.toList())", FieldNamer.getSetterRepeatedName(it.name))
                .build()
        })

        // create an extensions property for map fields
        wrapperBuilder.addProperties(type.mapMappedFields(types) {
            val (keyType, valueType) = it.describeMap(types)
            val mapType = Map::class.asClassName().parameterizedBy(
                keyType.asClassName(types),
                valueType.asClassName(types)
            )
            val propertyName = FieldNamer.getFieldName(it.name)

            PropertySpec.builder(propertyName, mapType)
                .mutable(true)
                .getter(
                    FunSpec.getterBuilder()
                        .addModifiers(KModifier.INLINE)
                        .addStatement("return builder.%L", FieldNamer.getAccessorMapName(it.name))
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addModifiers(KModifier.INLINE)
                        .addParameter("values", mapType)
                        .addCode(
                            """
                            |builder.clear%L()
                            |builder.%L(values)
                            |""".trimMargin(),
                            propertyName.capitalize(),
                            FieldNamer.getSetterMapName(it.name)
                        ).build()
                ).build()
        })

        // create an extensions property for map fields
        wrapperBuilder.addFunctions(type.mapMappedFields(types) {
            val (keyType, valueType) = it.describeMap(types)
            val pairType = Pair::class.asClassName().parameterizedBy(
                keyType.asClassName(types),
                valueType.asClassName(types)
            )
            val propertyName = FieldNamer.getFieldName(it.name)

            FunSpec.builder(propertyName)
                .addModifiers(KModifier.INLINE)
                .addParameter(
                    ParameterSpec.builder("values", pairType, KModifier.VARARG)
                        .build()
                )
                .addStatement("builder.%L(values.toMap())", FieldNamer.getSetterMapName(it.name))
                .build()
        })

        // create builder function that uses the wrapper
        val builderFun = FunSpec.builder(getBuilderFunName(type))
            .returns(type.className)
            .addParameter(
                "init",
                LambdaTypeName.get(
                    wrapperClass, listOf(), Unit::class.asTypeName()
                )
            )
            .addCode(
                """
                |val builder = %T.newBuilder()
                |%T(builder).apply(init)
                |return builder.build()
                |""".trimMargin(),
                type.className,
                wrapperClass
            )
            .build()

        // get list of builder functions in this package and append to it
        builders.addTo(
            type.className.packageName,
            types = listOf(wrapperBuilder.build()),
            functions = listOf(builderFun)
        )
    }

    // constructs a name for the builder function
    private fun getBuilderFunName(type: TypeInfo): String {
        var parentType = type.className.enclosingClassName()
        val parentTypes = mutableListOf<String>()
        while (parentType != null) {
            parentTypes.add(parentType.simpleName)
            parentType = parentType.enclosingClassName()
        }

        return if (parentTypes.isEmpty()) {
            type.className.simpleName
        } else {
            // TODO: better way to name w/o collisions?
            "${parentTypes.reversed().joinToString("_")}_${type.className.simpleName}"
        }.decapitalize()
    }
}

private data class TypeInfo(val proto: DescriptorProtos.DescriptorProto, val className: ClassName)

private class PackagesToBuilders {

    private class Parts(
        val types: MutableList<TypeSpec> = mutableListOf(),
        val functions: MutableList<FunSpec> = mutableListOf(),
        val properties: MutableList<PropertySpec> = mutableListOf()
    )

    private val builders: MutableMap<String, Parts> = mutableMapOf()

    fun addTo(
        packageName: String,
        types: List<TypeSpec> = listOf(),
        functions: List<FunSpec> = listOf(),
        properties: List<PropertySpec> = listOf()
    ) {
        val parts = builders[packageName] ?: Parts()
        if (!builders.containsKey(packageName)) {
            builders[packageName] = parts
        }
        parts.types += types
        parts.functions += functions
        parts.properties += properties
    }

    fun asGeneratedSources() =
        builders.keys.mapNotNull { asGeneratedSourceFor(it) }

    fun asGeneratedSourceFor(packageName: String) = builders[packageName]?.let {
        GeneratedSource(
            packageName,
            "KotlinBuilders",
            types = it.types,
            functions = it.functions,
            properties = it.properties
        )
    }
}

private fun <T> TypeInfo.mapNormalFields(types: ProtobufTypeMapper, t: (DescriptorProtos.FieldDescriptorProto) -> T) =
    this.proto.fieldList
        .filter { !it.isRepeated() && !it.isMap(types) }
        .map { t(it) }

private fun <T> TypeInfo.mapRepeatedFields(types: ProtobufTypeMapper, t: (DescriptorProtos.FieldDescriptorProto) -> T) =
    this.proto.fieldList
        .filter { it.isRepeated() && !it.isMap(types) }
        .map { t(it) }

private fun <T> TypeInfo.mapMappedFields(types: ProtobufTypeMapper, t: (DescriptorProtos.FieldDescriptorProto) -> T) =
    this.proto.fieldList
        .filter { it.isRepeated() && it.isMap(types) }
        .map { t(it) }
