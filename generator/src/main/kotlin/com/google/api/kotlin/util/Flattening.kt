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

package com.google.api.kotlin.util

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.PropertyPath
import com.google.api.kotlin.util.RequestObject.getBuilder
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asTypeName

/**
 * Utility for determining method flattening configurations.
 */
internal object Flattening {

    /** Get the parameters to flatten the [method] using the given [config] and [context]. */
    fun getFlattenedParameters(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        config: FlattenedMethod
    ): FlattenedMethodResult {
        val protoType = context.typeMap.getProtoTypeDescriptor(method.inputType)
        val kotlinType = context.typeMap.getKotlinType(method.inputType)
        val (parameters, requestObj) = getBuilder(context, protoType, kotlinType, config.parameters)
        return FlattenedMethodResult(parameters, requestObj, config)
    }

    fun visitFlattenedMethod(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        parametersAsPaths: List<PropertyPath>,
        visitor: Visitor
    ) = visitType(
        context,
        context.typeMap.getProtoTypeDescriptor(method.inputType),
        parametersAsPaths,
        visitor
    )

    abstract class Visitor {
        open fun onBegin(params: List<ParameterInfo>) {}
        open fun onEnd() {}
        open fun onNestedParam(currentPath: PropertyPath, fieldInfo: ProtoFieldInfo) {}
        open fun onTerminalParam(currentPath: PropertyPath, fieldInfo: ProtoFieldInfo) {}
    }

    fun visitType(
        context: GeneratorContext,
        requestType: DescriptorProtos.DescriptorProto,
        parametersAsPaths: List<PropertyPath>,
        visitor: Visitor
    ) {
        // create parameter list
        val parameters = parametersAsPaths.map { path ->
            val fieldInfo = getProtoFieldInfoForPath(context, path, requestType)
            val rawType = fieldInfo.field.asClassName(context.typeMap)
            val typeName = when {
                fieldInfo.field.isMap(context.typeMap) -> {
                    val (keyType, valueType) = fieldInfo.field.describeMap(context.typeMap)
                    Map::class.asTypeName().parameterizedBy(
                        keyType.asClassName(context.typeMap),
                        valueType.asClassName(context.typeMap)
                    )
                }
                fieldInfo.field.isRepeated() -> List::class.asTypeName().parameterizedBy(rawType)
                else -> rawType
            }
            val spec =
                ParameterSpec.builder(FieldNamer.getParameterName(path.lastSegment), typeName)
                    .build()
            ParameterInfo(spec, path, fieldInfo)
        }
        visitor.onBegin(parameters)

        // go through the nested properties from left to right
        val maxWidth = parametersAsPaths.map { it.size }.max() ?: 0
        for (i in 1..maxWidth) {
            // terminal node - set the value
            parametersAsPaths.filter { it.size == i }.forEach { path ->
                val currentPath = path.subPath(0, i)
                val field = getProtoFieldInfoForPath(context, path, requestType)

                visitor.onTerminalParam(currentPath, field)
            }

            // non terminal - ensure a builder exists
            parametersAsPaths.filter { it.size > i }.forEach { path ->
                val currentPath = path.subPath(0, i)
                val fieldInfo = getProtoFieldInfoForPath(context, currentPath, requestType)

                visitor.onNestedParam(currentPath, fieldInfo)
            }
        }

        visitor.onEnd()
    }
}

/**
 * The result of a flattened method with the given [config] including the [parameters]
 * for the method declaration and the [requestObject] that should be passed to the
 * underlying (original) method.
 */
internal data class FlattenedMethodResult(
    val parameters: List<ParameterInfo>,
    val requestObject: CodeBlock,
    val config: FlattenedMethod
)
