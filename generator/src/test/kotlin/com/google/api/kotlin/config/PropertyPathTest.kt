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

import kotlin.test.Test
import com.google.common.truth.Truth.assertThat

internal class PropertyPathTest {

    @Test
    fun `reports size`() {
        val path = PropertyPath(listOf())

        assertThat(path.size).isEqualTo(0)
    }

    @Test
    fun `reports size and first and last`() {
        val path = PropertyPath(listOf("foo", "mid", "bar"))

        assertThat(path.size).isEqualTo(3)
        assertThat(path.firstSegment).isEqualTo("foo")
        assertThat(path.lastSegment).isEqualTo("bar")
        assertThat(path.last).isEqualTo(PropertyPath("bar"))
    }

    @Test
    fun `can come from a string`() {
        val path = "one.two.three".asPropertyPath()

        assertThat(path).isEqualTo(PropertyPath(listOf("one", "two", "three")))
    }

    @Test
    fun `can come from a string with idx`() {
        val path = "bar[0].two[1].a_b".asPropertyPath()

        assertThat(path).isEqualTo(PropertyPath(listOf("bar[0]", "two[1]", "a_b")))
    }

    @Test
    fun `can become a string`() {
        val path = PropertyPath(listOf("aa", "bb", "cc"))

        assertThat(path.toString()).isEqualTo("aa.bb.cc")
    }

    @Test
    fun `can become a string with idx`() {
        val path = PropertyPath(listOf("_aa", "bb[9]", "c_c"))

        assertThat(path.toString()).isEqualTo("_aa.bb[9].c_c")
    }

    @Test
    fun `can become a sub path`() {
        val path = PropertyPath(listOf("aa", "bb", "cc", "foo"))

        assertThat(path.subPath(1, 3)).isEqualTo(PropertyPath(listOf("bb", "cc")))
    }

    @Test
    fun `can take`() {
        val path = PropertyPath(listOf("aa", "bb", "cc", "foo"))

        assertThat(path.takeSubPath(2)).isEqualTo(PropertyPath(listOf("aa", "bb")))
    }

    @Test
    fun `merges distinct`() {
        val one = listOf(
            PropertyPath(listOf("a", "b", "c")),
            PropertyPath(listOf("x", "y", "z"))
        )
        val two = listOf(
            PropertyPath(listOf("a", "b", "c"))
        )
        val three = listOf(
            PropertyPath(listOf("x", "y", "z"))
        )

        val merged = one.merge(two, three)

        assertThat(merged).containsExactly(
            PropertyPath(listOf("a", "b", "c")),
            PropertyPath(listOf("x", "y", "z"))
        )
        assertThat(merged.size).isEqualTo(2)
    }

    @Test
    fun `does not use extra sample properties`() {
        val path = listOf(
            PropertyPath(listOf("a")),
            PropertyPath(listOf("a", "b")),
            PropertyPath(listOf("x", "y", "z"))
        )

        val merged = path.merge(SampleMethod(listOf(
            SampleParameterAndValue("a.b.c", "9"),
            SampleParameterAndValue("z", "zee")
        )))

        assertThat(merged).containsExactly(
            PropertyPath(listOf("a", "b", "c")),
            PropertyPath(listOf("x", "y", "z"))
        )
        assertThat(merged.size).isEqualTo(2)
    }

    @Test
    fun `merges null without change`() {
        val path = listOf(
            PropertyPath("sink"),
            PropertyPath("sink_name"))

        val merged = path.merge(null)

        assertThat(merged).containsExactly(
            PropertyPath("sink"),
            PropertyPath("sink_name")
        )
        assertThat(merged.size).isEqualTo(2)
    }
}
