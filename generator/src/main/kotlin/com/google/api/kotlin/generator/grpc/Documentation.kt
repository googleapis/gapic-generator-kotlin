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

package com.google.api.kotlin.generator.grpc

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.SampleMethod
import com.google.api.kotlin.util.FieldNamer
import com.google.api.kotlin.util.ParameterInfo
import com.google.api.kotlin.util.RequestObject.getBuilder
import com.google.api.kotlin.util.getMethodComments
import com.google.api.kotlin.util.getParameterComments
import com.google.api.kotlin.util.getProtoFieldInfoForPath
import com.google.api.kotlin.util.isMessageType
import com.google.api.kotlin.wrap
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.CodeBlock

/** Generates the KDoc documentation. */
internal interface Documentation {
    fun generateClassKDoc(ctx: GeneratorContext): CodeBlock
    fun generateMethodKDoc(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        samples: List<SampleMethod>,
        flatteningConfig: FlattenedMethod? = null,
        parameters: List<ParameterInfo> = listOf(),
        paging: PagedResponse? = null,
        extras: List<CodeBlock> = listOf()
    ): CodeBlock
}

internal class DocumentationImpl : Documentation {

    override fun generateClassKDoc(context: GeneratorContext): CodeBlock {
        val doc = CodeBlock.builder()
        val m = context.metadata

        // add primary (summary) section
        doc.add(
            """
            |%L
            |
            |%L
            |
            |[Product Documentation](%L)
            |""".trimMargin(),
            m.branding.name, m.branding.summary.wrap(), m.branding.url
        )

        // TODO: add other sections (quick start, etc.)

        return doc.build()
    }

    // create method comments from proto comments
    override fun generateMethodKDoc(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        samples: List<SampleMethod>,
        flatteningConfig: FlattenedMethod?,
        parameters: List<ParameterInfo>,
        paging: PagedResponse?,
        extras: List<CodeBlock>
    ): CodeBlock {
        val doc = CodeBlock.builder()

        // remove the spacing from proto files
        fun cleanupComment(text: String?) = text
            ?.replace("\\n\\s".toRegex(), "\n")
            ?.trim()

        // add proto comments
        val text = context.proto.getMethodComments(context.service, method)
        doc.add("%L\n\n", cleanupComment(text) ?: "")

        // add any samples
        if (samples.isEmpty()) {
            doc.add(generateMethodSample(context, method, methodName, null, flatteningConfig, paging))
        } else {
            for (sample in samples) {
                doc.add(
                    generateMethodSample(
                        context, method, methodName, sample, flatteningConfig, paging
                    )
                )
            }
        }

        // add parameter comments
        val paramComments = flatteningConfig?.parameters?.asSequence()?.mapIndexed { idx, path ->
            val fieldInfo = getProtoFieldInfoForPath(
                context, path, context.typeMap.getProtoTypeDescriptor(method.inputType)
            )
            val comment = fieldInfo.file.getParameterComments(fieldInfo)
            Pair(parameters[idx].spec.name, cleanupComment(comment))
        }?.filter { it.second != null }?.toList() ?: listOf()
        paramComments.forEach { doc.add("\n@param %L %L\n", it.first, it.second) }

        // add any extra comments at the bottom (only used for the pageSize currently)
        extras.forEach { doc.add("\n%L\n", it) }

        // put it all together
        return doc.build()
    }

    private fun generateMethodSample(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        sample: SampleMethod?,
        flatteningConfig: FlattenedMethod?,
        paging: PagedResponse? = null
    ): CodeBlock {
        val call = CodeBlock.builder()

        // create client
        call.addStatement("For example:")
        call.addStatement("```")
        call.addStatement(
            "val client = %T.fromServiceAccount(YOUR_KEY_FILE)",
            context.className
        )

        // create inputs
        val inputType = context.typeMap.getProtoTypeDescriptor(method.inputType)
        val invokeClientParams = if (flatteningConfig != null) {
            flatteningConfig.parameters.map { p ->
                val type = getProtoFieldInfoForPath(context, p, inputType)
                if (type.field.isMessageType()) {
                    getBuilder(
                        context, type.message, type.kotlinType, listOf(p.last), sample
                    ).builder
                } else {
                    val prop = FieldNamer.getAccessorName(
                        sample?.parameters?.find { it.parameterPath == p.toString() }?.value
                            ?: p.toString()
                    )
                    CodeBlock.of("%L", prop)
                }
            }
        } else {
            val inputKotlinType = context.typeMap.getKotlinType(method.inputType)
            listOf(getBuilder(context, inputType, inputKotlinType, listOf(), sample).builder)
        }

        // fix indentation (can we make the formatter fix this somehow?)
        val indentedParams = invokeClientParams.map { indentBuilder(context, it, 1) }

        // invoke method
        if (paging != null) {
            call.add(
                """
                |val pager = client.%N(
                |    ${invokeClientParams.joinToString(",\n    ") { "%L" }}
                |)
                |val page = pager.next()
                |""".trimMargin(),
                methodName,
                *indentedParams.toTypedArray()
            )
        } else {
            call.add(
                """
                |val result = client.%N(
                |    ${invokeClientParams.joinToString(",\n    ") { "%L" }}
                |)
                |""".trimMargin(),
                methodName,
                *indentedParams.toTypedArray()
            )
        }

        // close
        call.addStatement("```")

        return call.build()
    }

    /**
     * Kotlin Poet doesn't handle indented code within Kdoc well so we use this to
     * correct the indentations for sample code. It would be nice to find an alternative.
     */
    private fun indentBuilder(context: GeneratorContext, code: CodeBlock, level: Int): CodeBlock {
        val indent = " ".repeat(level * 4)

        // we will get the fully qualified names when turning the code block into a string
        val specialPackages = "(${context.className.packageName}|com.google.api)"

        // remove fully qualified names and adjust indent
        val formatted = code.toString()
            .replace("\n", "\n$indent")
            .replace("^[ ]*$specialPackages\\.".toRegex(), "")
            .replace(" = $specialPackages\\.".toRegex(), " = ")
        return CodeBlock.of("%L", formatted)
    }
}
