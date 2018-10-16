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

/**
 * Utilities for creating field names, getters, setters, and accessors.
 */
internal object FieldNamer {

    /*
     * Create setter code based on type of field (map vs. repeated, vs. single object) using
     * the Java builder for the type.
     */
    fun getSetterCode(
        typeMap: ProtobufTypeMapper,
        fieldInfo: ProtoFieldInfo,
        value: CodeBlock,
        useDSLBuilder: Boolean
    ): CodeBlock {
        // use implicit this in dsl builders
        val qualifier = if (useDSLBuilder) {
            ""
        } else {
            "."
        }

        // map and repeated fields
        if (fieldInfo.field.isMap(typeMap)) {
            return CodeBlock.of(
                "$qualifier${getSetterMapName(fieldInfo.field.name)}(%L)",
                value
            )
        } else if (fieldInfo.field.isRepeated()) {
            return if (fieldInfo.index >= 0) {
                CodeBlock.of(
                    "$qualifier${getSetterRepeatedAtIndexName(fieldInfo.field.name)}(${fieldInfo.index}, %L)",
                    value
                )
            } else {
                CodeBlock.of(
                    "$qualifier${getSetterRepeatedName(fieldInfo.field.name)}(%L)",
                    value
                )
            }
        }

        // normal fields
        return if (useDSLBuilder) {
            CodeBlock.of("${getAccessorName(fieldInfo.field.name, value)} = %L", value)
        } else {
            CodeBlock.of("$qualifier${getSetterName(fieldInfo.field.name)}(%L)", value)
        }
    }

    fun getSetterCode(
        typeMap: ProtobufTypeMapper,
        fieldInfo: ProtoFieldInfo,
        value: String,
        useDSLBuilder: Boolean
    ) =
        getSetterCode(typeMap, fieldInfo, CodeBlock.of("%L", value), useDSLBuilder)

    fun getAccessorName(typeMap: ProtobufTypeMapper, fieldInfo: ProtoFieldInfo): String {
        if (fieldInfo.field.isMap(typeMap)) {
            return getAccessorMapName(fieldInfo.field.name)
        } else if (fieldInfo.field.isRepeated()) {
            return if (fieldInfo.index >= 0) {
                "${getAccessorRepeatedAtIndexName(fieldInfo.field.name)}[${fieldInfo.index}]"
            } else {
                getAccessorRepeatedName(fieldInfo.field.name)
            }
        }
        return getAccessorName(fieldInfo.field.name)
    }

    fun getFieldName(protoFieldName: String) =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)

    fun getSetterMapName(protoFieldName: String) =
        "putAll" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getSetterRepeatedName(protoFieldName: String) =
        "addAll" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getSetterRepeatedAtIndexName(protoFieldName: String) =
        "add" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getSetterName(protoFieldName: String) =
        "set" + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, protoFieldName)

    fun getAccessorMapName(protoFieldName: String) =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName) + "Map"

    fun getAccessorName(protoFieldName: String, value: CodeBlock? = null) =
        getQualifier(protoFieldName, value) + CaseFormat.LOWER_UNDERSCORE.to(
            CaseFormat.LOWER_CAMEL,
            protoFieldName
        ) + ""

    fun getAccessorRepeatedAtIndexName(protoFieldName: String, value: CodeBlock? = null) =
        getQualifier(protoFieldName, value) + CaseFormat.LOWER_UNDERSCORE.to(
            CaseFormat.LOWER_CAMEL,
            protoFieldName
        ) + ""

    fun getAccessorRepeatedName(protoFieldName: String, value: CodeBlock? = null) =
        getQualifier(protoFieldName, value) + CaseFormat.LOWER_UNDERSCORE.to(
            CaseFormat.LOWER_CAMEL,
            protoFieldName
        ) + "List"

    fun getParameterName(protoFieldName: String, value: CodeBlock? = null) =
        getQualifier(protoFieldName, value) + CaseFormat.LOWER_UNDERSCORE.to(
            CaseFormat.LOWER_CAMEL,
            protoFieldName
        ) + ""

    fun getQualifier(protoFieldName: String, value: CodeBlock? = null): String {
        val name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, protoFieldName)
        return if (name == value.toString()) {
            "this."
        } else {
            ""
        }
    }
}