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
import com.google.api.kotlin.config.PropertyPath
import com.google.api.kotlin.config.SampleMethod
import com.google.api.kotlin.config.asPropertyPath
import com.google.api.kotlin.config.merge
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

/**
 * Utility to build a request object for an API method call.
 */
internal object RequestObject {

    /**
     * A [builder] CodeBlock that can construct an proto object as a Kotlin type
     * along with it's corresponding [parameters].
     */
    data class BuilderCodeBlock(
        val parameters: List<ParameterInfo>,
        val builder: CodeBlock
    )

    /**
     * Get a builder CodeBlock for constructing the [messageType] via it's [kotlinType]
     * with the given [propertyPaths] setters filled out.
     *
     * If [sample] is non-null and it contains a property path that matches an entry in
     * [propertyPaths] it's value is used for the setter. Otherwise, the property name
     * is used as the variable name given to the setter.
     */
    fun getBuilder(
        context: GeneratorContext,
        messageType: DescriptorProtos.DescriptorProto,
        kotlinType: ClassName,
        propertyPaths: List<PropertyPath>,
        sample: SampleMethod? = null
    ): BuilderCodeBlock {
        // params for the method signature
        lateinit var parameters: List<ParameterInfo>

        // the set of builders to be used to create the request object
        val builders = mutableMapOf<String, CodeBlockBuilder>()

        // add outermost builder
        val code = CodeBlock.builder()
            .add("%T {\n", builderName(kotlinType))
            .indent()

        Flattening.visitType(
            context,
            messageType,
            propertyPaths.merge(sample),
            object : Flattening.Visitor() {
                override fun onBegin(params: List<ParameterInfo>) {
                    parameters = params
                }

                override fun onTerminalParam(paramName: String, currentPath: PropertyPath, fieldInfo: ProtoFieldInfo) {
                    // check if an explicit value was set for this property
                    // if not use the parameter name
                    val explicitValue = sample?.parameters?.find {
                        it.parameterPath == currentPath.toString()
                    }
                    val value =
                        explicitValue?.value ?: paramName // FieldNamer.getFieldName(fieldInfo.field.name)

                    // set value or add to appropriate builder
                    val setterCode =
                        FieldNamer.getDslSetterCode(context.typeMap, fieldInfo, value)
                    if (currentPath.size == 1) {
                        code.addStatement("%L", setterCode)
                    } else {
                        val key = currentPath.takeSubPath(currentPath.size - 1).toString()
                        builders[key]!!.code.addStatement("%L", setterCode)
                    }
                }

                override fun onNestedParam(paramName: String, currentPath: PropertyPath, fieldInfo: ProtoFieldInfo) {
                    // create a builder for this param, if first time
                    val key = currentPath.toString()
                    if (!builders.containsKey(key)) {
                        val nestedBuilder = CodeBlock.builder()
                            .add(
                                "%T {\n",
                                builderName(context.typeMap.getKotlinType(fieldInfo.field.typeName))
                            )
                            .indent()
                        builders[key] = CodeBlockBuilder(nestedBuilder, fieldInfo)
                    }
                }

                override fun onEnd() {
                    // close the nested builders
                    builders.forEach { _, builder ->
                        builder.code
                            .add("}\n")
                            .unindent()
                    }

                    // build from innermost to outermost
                    builders.keys.map { it.split(".") }.sortedBy { it.size }.reversed()
                        .map { it.asPropertyPath() }
                        .forEach { currentPath ->
                            val builder = builders[currentPath.toString()]!!
                            code.add(
                                FieldNamer.getDslSetterCode(
                                    context.typeMap, builder.fieldInfo, builder.code.build()
                                )
                            )
                        }
                }
            })

        // close outermost builder
        code.unindent().add("}")

        return BuilderCodeBlock(parameters, code.build())
    }

    private fun builderName(className: ClassName) = className.peerClass(className.simpleName.decapitalize())

    private class CodeBlockBuilder(
        val code: CodeBlock.Builder,
        val fieldInfo: ProtoFieldInfo
    )
}