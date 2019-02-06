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

import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.common.base.CaseFormat
import com.squareup.kotlinpoet.CodeBlock
import mu.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Utilities for creating field names, getters, setters, and accessors.
 */
internal object FieldNamer {

    /*
     * Create setter code based on type of field (map vs. repeated, vs. single object) using
     * the DSL builder for the type.
     */
    fun getDslSetterCode(
        typeMap: ProtobufTypeMapper,
        fieldInfo: ProtoFieldInfo,
        value: CodeBlock
    ): CodeBlock = when {
        fieldInfo.field.isMap(typeMap) -> {
            val qualifier = getQualifier(fieldInfo.field.name, value)
            val name = getDslSetterMapName(fieldInfo.field.name)
            CodeBlock.of(
                "$qualifier$name = %L",
                value
            )
        }
        fieldInfo.field.isRepeated() -> {
            val qualifier = getQualifier(fieldInfo.field.name, value)
            if (fieldInfo.index >= 0) {
                log.warn { "Indexed setter operations currently ignore the specified index! (${fieldInfo.message.name}.${fieldInfo.field.name})" }
                CodeBlock.of(
                    "$qualifier${getDslSetterRepeatedNameAtIndex(fieldInfo.field.name)}(%L)",
                    value
                )
            } else {
                val name = getDslSetterRepeatedName(fieldInfo.field.name)
                CodeBlock.of(
                    "$qualifier$name = %L",
                    value
                )
            }
        }
        else -> { // normal fields
            val qualifier = getQualifier(fieldInfo.field.name, value)
            val name = getFieldName(fieldInfo.field.name)
            CodeBlock.of("$qualifier$name = %L", value)
        }
    }

    fun getDslSetterCode(
        typeMap: ProtobufTypeMapper,
        fieldInfo: ProtoFieldInfo,
        value: String
    ) = getDslSetterCode(typeMap, fieldInfo, CodeBlock.of("%L", value))

    /**
     * Get the accessor field name for a Java proto message type.
     */
    fun getJavaAccessorName(typeMap: ProtobufTypeMapper, fieldInfo: ProtoFieldInfo): String {
        if (fieldInfo.field.isMap(typeMap)) {
            return getJavaBuilderAccessorMapName(fieldInfo.field.name)
        } else if (fieldInfo.field.isRepeated()) {
            return if (fieldInfo.index >= 0) {
                "${getJavaBuilderAccessorRepeatedName(fieldInfo.field.name)}[${fieldInfo.index}]"
            } else {
                getJavaBuilderAccessorRepeatedName(fieldInfo.field.name)
            }
        }
        return getJavaBuilderAccessorName(fieldInfo.field.name)
    }

    fun getFieldName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    private fun getDslSetterMapName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    private fun getDslSetterRepeatedName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    private fun getDslSetterRepeatedNameAtIndex(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    fun getJavaBuilderSetterMapName(protoFieldName: String): String =
        "putAll" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getJavaBuilderSetterRepeatedName(protoFieldName: String): String =
        "addAll" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getJavaBuilderRawSetterName(protoFieldName: String): String =
        "set" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getJavaBuilderSyntheticSetterName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    fun getJavaBuilderAccessorMapName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName) + "Map"

    fun getJavaBuilderAccessorRepeatedName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName) + "List"

    fun getJavaBuilderAccessorName(protoFieldName: String): String =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    private fun getQualifier(protoFieldName: String, value: CodeBlock? = null): String {
        val name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)
        return if (name == value.toString()) {
            "this."
        } else {
            ""
        }
    }
}
