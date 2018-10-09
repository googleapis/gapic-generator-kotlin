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

package com.google.api.kotlin

import org.junit.rules.ExternalResource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/** I/O helpers for tests */
class StdInAndOutResource(
    private val echoStdout: Boolean = false,
    private val echoStderr: Boolean = true
) : ExternalResource() {

    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    private val realOut = System.out
    private val realErr = System.err
    private val realIns = System.`in`

    val stdout
        get() = out.toString("UTF-8")

    val stdoutBytes
        get() = out.toByteArray()

    val stderr
        get() = err.toString("UTF-8")

    var stdIn = ByteArrayInputStream(ByteArray(0))
        set(value) = System.setIn(value)

    override fun before() {
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(err))
        System.setIn(stdIn)
    }

    override fun after() {
        System.setOut(realOut)
        System.setErr(realErr)
        System.setIn(realIns)

        if (echoStdout) {
            System.out.println(out.toString())
        }
        if (echoStderr) {
            System.err.println(err.toString())
        }

        out.reset()
        err.reset()
    }
}