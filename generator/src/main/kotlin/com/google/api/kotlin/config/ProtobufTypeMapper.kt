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

package com.google.api.kotlin.config

import com.google.common.base.CaseFormat
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName

/** Maps proto names to kotlin names */
internal class ProtobufTypeMapper private constructor() {

    private val typeMap = mutableMapOf<String, String>()
    private val serviceMap = mutableMapOf<String, String>()

    private val knownProtoTypes = mutableMapOf<String, DescriptorProtos.DescriptorProto>()
    private val knownProtoEnums = mutableMapOf<String, DescriptorProtos.EnumDescriptorProto>()

    /** Lookup the Kotlin type given the proto type. */
    fun getKotlinType(protoType: String) =
        ClassName.bestGuess(
            typeMap[protoType]
                ?: throw IllegalArgumentException("proto type: $protoType is not recognized")
        )

    /** Lookup the Kotlin type given a proto service */
    fun getKotlinGrpcType(protoService: String, suffix: String) =
        ClassName.bestGuess("${serviceMap[protoService] ?: ""}$suffix")

    fun getKotlinGrpcTypeInnerClass(protoService: String, suffix: String, innerClassName: String) =
        ClassName("${serviceMap[protoService] ?: ""}$suffix", innerClassName)

    fun getKotlinGrpcTypeInnerClass(
        proto: DescriptorProtos.FileDescriptorProto,
        service: DescriptorProtos.ServiceDescriptorProto,
        suffix: String,
        innerClassName: String
    ) =
        getKotlinGrpcTypeInnerClass(".${proto.`package`}.${service.name}", suffix, innerClassName)

    fun getKotlinGrpcType(
        proto: DescriptorProtos.FileDescriptorProto,
        service: DescriptorProtos.ServiceDescriptorProto,
        suffix: String
    ) =
        getKotlinGrpcType(".${proto.`package`}.${service.name}", suffix)

    /** Get all Kotlin types (excluding enums and map types) */
    fun getAllKotlinTypes() = knownProtoTypes.keys
        .filter { !(knownProtoTypes[it]?.options?.mapEntry ?: false) }
        .map { typeMap[it] ?: throw IllegalStateException("unknown type: $it") }

    /** Checks if the message type is in this mapper */
    fun hasProtoTypeDescriptor(type: String) = knownProtoTypes.containsKey(type)

    /** Lookup up a known proto message type by name */
    fun getProtoTypeDescriptor(type: String) = knownProtoTypes[type]
        ?: throw IllegalArgumentException("unknown type: $type")

    /** Checks if the enum type is in this mapper */
    fun hasProtoEnumDescriptor(type: String) = knownProtoEnums.containsKey(type)

    /** Lookup a known proto enum type by name */
    fun getProtoEnumDescriptor(type: String) = knownProtoEnums[type]
        ?: throw IllegalArgumentException("unknown enum: $type")

    override fun toString(): String {
        val ret = StringBuilder("Types:")
        typeMap.forEach { k, v -> ret.append("\n  $k -> $v") }
        return ret.toString()
    }

    companion object {
        /** Create a map from a set of proto descriptors */
        fun fromProtos(descriptors: Collection<DescriptorProtos.FileDescriptorProto>): ProtobufTypeMapper {
            val map = ProtobufTypeMapper()

            descriptors.map { proto ->
                val protoPackage = if (proto.hasPackage()) "." + proto.`package` else ""
                val javaPackage = if (proto.options.hasJavaPackage())
                    proto.options.javaPackage
                else
                    proto.`package`

                val enclosingClassName = if (proto.options?.javaMultipleFiles != false)
                    null
                else
                    getOuterClassname(proto)

                fun addMsg(p: DescriptorProtos.DescriptorProto, parent: String) {
                    val key = "$protoPackage$parent.${p.name}"
                    map.typeMap[key] = listOf("$javaPackage$parent", enclosingClassName, p.name)
                        .filterNotNull()
                        .joinToString(".")
                    map.knownProtoTypes[key] = p
                }

                fun addEnum(p: DescriptorProtos.EnumDescriptorProto, parent: String) {
                    val key = "$protoPackage$parent.${p.name}"
                    map.typeMap[key] = listOf("$javaPackage$parent", enclosingClassName, p.name)
                        .filterNotNull()
                        .joinToString(".")
                    map.knownProtoEnums[key] = p
                }

                fun addService(p: DescriptorProtos.ServiceDescriptorProto, parent: String) {
                    map.serviceMap["$protoPackage$parent.${p.name}"] =
                        listOf("$javaPackage$parent", enclosingClassName, p.name)
                            .filterNotNull()
                            .joinToString(".")
                }

                fun addNested(p: DescriptorProtos.DescriptorProto, parent: String) {
                    addMsg(p, parent)
                    p.enumTypeList.forEach { addEnum(it, "$parent.${p.name}") }
                    p.nestedTypeList.forEach { addNested(it, "$parent.${p.name}") }
                }

                // process top level types and services
                proto.messageTypeList.forEach { addNested(it, "") }
                proto.serviceList.forEach { addService(it, "") }
                proto.enumTypeList.forEach { addEnum(it, "") }
            }

            return map
        }

        private fun getOuterClassname(proto: DescriptorProtos.FileDescriptorProto): String {
            if (proto.options.hasJavaOuterClassname()) {
                return proto.options.javaOuterClassname
            }

            var fileName = proto.name.substring(0, proto.name.length - ".proto".length)
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf('/') + 1)
            }

            fileName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, fileName)
            if (proto.enumTypeList.any { it.name.equals(fileName) } ||
                proto.messageTypeList.any { it.name.equals(fileName) } ||
                proto.serviceList.any { it.name.equals(fileName) }) {
                fileName += "OuterClass"
            }

            return fileName
        }
    }
}