/*
 * Copyright 2019 Google LLC
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

import com.github.shyiko.ktlint.core.KtLint
import com.github.shyiko.ktlint.ruleset.standard.StandardRuleSetProvider
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import mu.KotlinLogging

private val RULES = listOf(StandardRuleSetProvider().get())

private val log = KotlinLogging.logger {}

internal fun FileSpec.format(): String = format(this.toString())
internal fun CodeBlock.format(): String = format(this.toString())

internal fun CodeBlock.formatSample(): String {
    val code = CodeBlock.of("fun example() { \n%L\n }", this)

    return code.format()
        .replace(Regex("\\A\\s*fun\\s+example\\s*\\(\\)\\s*\\{\\s*"), "")
        .replace(Regex("\\s*\\}\\s*\\z"), "")
}

private fun format(source: String): String = try {
    KtLint.format(source, RULES) { e, _ ->
        log.trace { "Lint: ${e.line}:${e.col} ${e.detail} (${e.ruleId})" }
    }
} catch (t: Throwable) {
    log.error(t) { "Code could not be formatted (using original)" }
    source
}
