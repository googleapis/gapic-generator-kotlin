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
    val last get() = this.segments.last().asPropertyPath()
    val size get() = this.segments.size

    constructor(segment: String): this(listOf(segment))

    /** path between the specified [fromIndex] (inclusive) and [toIndex] (exclusive). */
    fun subPath(fromIndex: Int, toIndex: Int) =
        PropertyPath(segments.subList(fromIndex, toIndex))

    /** sub path using the first [n] elements */
    fun takeSubPath(n: Int) = PropertyPath(segments.take(n))

    override fun toString() = segments.joinToString(".")

    companion object {
        /** merges the list of paths into a single list without duplicates */
        fun merge(vararg pathLists: List<PropertyPath>): List<PropertyPath> {
            val all = pathLists
                .flatMap { it.map { it.segments.joinToString(".") } }
                .distinct()

            // if there are properties such as "a.b.c" and "a.b"
            // we only want the most specific (i.e. "a.b.c")
            return all
                .filter { p ->
                    all.none { it != p && it.startsWith("$p.") }
                }
                .map { PropertyPath(it.split(".")) }
        }
    }
}

internal fun String.asPropertyPath() = PropertyPath(this.split("."))
internal fun List<String>.asPropertyPath() = PropertyPath(this)

internal fun List<PropertyPath>.merge(vararg pathLists: List<PropertyPath>) =
    PropertyPath.merge(this, *pathLists)

/**
 * Merges all paths from the sample configuration. Note that if the sample configuration
 * contains references to properties that are not in the original list they will be ignored.
 *
 * For example given the list: ["a.b" , "x"]
 * Sample properties will be included if they start with a or x but not y
 */
internal fun List<PropertyPath>.merge(sample: SampleMethod?): List<PropertyPath> {
    val pathsFromSamples = sample?.parameters?.map {
        PropertyPath(it.parameterPath.split("."))
    } ?: listOf()

    // remove parents if child nodes exists
    val paths = this.map { it.toString() }
    val usePathsFromSamples = pathsFromSamples.filter { p ->
        paths.any { p.toString().startsWith("$it.") }
    }

    return PropertyPath.merge(this, usePathsFromSamples)
}
