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
import com.google.api.kotlin.generator.AbstractGenerator
import com.google.api.kotlin.generator.wrap
import com.squareup.kotlinpoet.CodeBlock

/** Generates the top level (class) documentation. */
internal interface Documentation {
    fun generateClassDoc(ctx: GeneratorContext): CodeBlock
}

internal class DocumentationImpl : AbstractGenerator(), Documentation {

    override fun generateClassDoc(ctx: GeneratorContext): CodeBlock {
        val doc = CodeBlock.builder()
        val m = ctx.metadata

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
}
