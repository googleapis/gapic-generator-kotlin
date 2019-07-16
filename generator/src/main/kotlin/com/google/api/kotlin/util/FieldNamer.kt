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

import com.google.api.kotlin.config.PropertyPath
import com.google.api.kotlin.config.ProtobufTypeMapper
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
            val name = getDslSetterMapName(fieldInfo.field.name)
            CodeBlock.of(
                "this.$name = %L",
                value
            )
        }
        fieldInfo.field.isRepeated() -> {
            if (fieldInfo.index >= 0) {
                log.warn { "Indexed setter operations currently ignore the specified index! (${fieldInfo.message.name}.${fieldInfo.field.name})" }
                CodeBlock.of(
                    "this.${getDslSetterRepeatedNameAtIndex(fieldInfo.field.name)}(%L)",
                    value
                )
            } else {
                val name = getDslSetterRepeatedName(fieldInfo.field.name)
                CodeBlock.of(
                    "this.$name = %L",
                    value
                )
            }
        }
        else -> { // normal fields
            val name = getFieldName(fieldInfo.field.name)
            CodeBlock.of("this.$name = %L", value)
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

    fun getNestedFieldName(p: PropertyPath): String =
        p.segments.map { it.asVarName() }.joinToString(".")

    fun getFieldName(protoFieldName: String): String =
        protoFieldName.asVarName()

    private fun getDslSetterMapName(protoFieldName: String): String =
        protoFieldName.asVarName().escapeIfReserved()

    private fun getDslSetterRepeatedName(protoFieldName: String): String =
        protoFieldName.asVarName().escapeIfReserved()

    private fun getDslSetterRepeatedNameAtIndex(protoFieldName: String): String =
        protoFieldName.asVarName().escapeIfReserved()

    fun getJavaBuilderSetterMapName(protoFieldName: String): String =
        "putAll${protoFieldName.asVarName(false)}".escapeIfReserved()

    fun getJavaBuilderSetterRepeatedName(protoFieldName: String): String =
        "addAll${protoFieldName.asVarName(false)}".escapeIfReserved()

    fun getJavaBuilderRawSetterName(protoFieldName: String): String =
        "set${protoFieldName.asVarName(false)}".escapeIfReserved()

    fun getJavaBuilderSyntheticSetterName(protoFieldName: String): String =
        protoFieldName.asVarName().escapeIfReserved()

    fun getJavaBuilderAccessorMapName(protoFieldName: String): String =
        "${protoFieldName.asVarName()}Map".escapeIfReserved()

    fun getJavaBuilderAccessorRepeatedName(protoFieldName: String): String =
        "${protoFieldName.asVarName()}List".escapeIfReserved()

    fun getJavaBuilderAccessorName(protoFieldName: String): String =
        protoFieldName.asVarName().escapeIfReserved()

    private fun String.asVarName(isLower: Boolean = true): String =
        this.underscoresToCamelCase(!isLower)

    private val LEADING_UNDERSCORES = Regex("^(_)+")

    // this is taken from the protobuf utility of the same name
    // snapshot: https://github.com/protocolbuffers/protobuf/blob/61301f01552dd84d744a05c88af95833c600a1a7/src/google/protobuf/compiler/cpp/cpp_helpers.cc
    private fun String.underscoresToCamelCase(capitalize: Boolean): String {
        var cap = capitalize
        val result = StringBuffer()

        // addition to the protobuf rule to handle synthetic names
        var str = this.replace(LEADING_UNDERSCORES, "")

        for (char in str) {
            if (char.isLetter() && char.isLowerCase()) {
                result.append(if (cap) char.toUpperCase() else char)
                cap = false
            } else if (char.isLetter() && char.isUpperCase()) {
                result.append(char)
                cap = false
            } else if (char.isDigit()) {
                result.append(char)
                cap = true
            } else {
                cap = true
            }
        }
        str = result.toString()

        // addition to the protobuf rule to handle synthetic names
        if (!capitalize) {
            if (str.matches(Regex("[a-z0-9][A-Z0-9]+"))) {
                str = str.toLowerCase()
            }
        }
        return str
    }

    // TODO: can remove this when Kotlin poet releases %M support
    private fun String.escapeIfReserved() = if (KEYWORDS.contains(this)) "`$this`" else this

    private val KEYWORDS = setOf(
        "package",
        "as",
        "typealias",
        "class",
        "this",
        "super",
        "val",
        "var",
        "fun",
        "for",
        "null",
        "true",
        "false",
        "is",
        "in",
        "throw",
        "return",
        "break",
        "continue",
        "object",
        "if",
        "try",
        "else",
        "while",
        "do",
        "when",
        "interface",
        "typeof"
    )
}
