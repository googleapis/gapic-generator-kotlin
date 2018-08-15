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

/**
 * A path to a property composed of named segments.
 */
internal data class PropertyPath(
    val segments: List<String>
) {
    val firstSegment get() = this.segments.first()
    val lastSegment get() = this.segments.last()
    val size get() = this.segments.size

    /** path between the specified [fromIndex] (inclusive) and [toIndex] (exclusive). */
    fun subPath(fromIndex: Int, toIndex: Int) =
        PropertyPath(segments.subList(fromIndex, toIndex))

    /** sub path using the first [n] elements */
    fun takeSubPath(n: Int) = PropertyPath(segments.take(n))

    override fun toString() = segments.joinToString(".")

    companion object {
        /** merges the list of paths into a single list without duplicates */
        fun merge(vararg pathLists: List<PropertyPath>): List<PropertyPath> {
            val all = pathLists.flatMap { it.map { it.segments.joinToString(".") } }
            return all.distinct().map { PropertyPath(it.split(".")) }
        }
    }
}

internal fun String.asPropertyPath() = PropertyPath(this.split("."))
internal fun List<String>.asPropertyPath() = PropertyPath(this)

internal fun List<PropertyPath>.merge(vararg pathLists: List<PropertyPath>) =
    PropertyPath.merge(this, *pathLists)
internal fun List<PropertyPath>.merge(sample: SampleMethod?) =
    sample?.parameters?.map { PropertyPath(it.parameterPath.split(".")) }?.merge(this) ?: this
