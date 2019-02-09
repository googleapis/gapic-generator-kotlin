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

package com.google.api.kotlin

import com.google.devtools.common.options.Option
import com.google.devtools.common.options.OptionsBase

/** Command line commandLineOptions */
class ClientPluginOptions(
    @JvmField
    @Option(
        name = "help",
        abbrev = 'h',
        help = "Prints usage info.",
        defaultValue = "false"
    )
    var help: Boolean = false,

    @JvmField
    @Option(
        name = "input",
        abbrev = 'i',
        help = "A serialized code generation request proto (if not set it is read from stdin).",
        category = "io",
        defaultValue = ""
    )
    var inputFile: String = "",

    @JvmField
    @Option(
        name = "output",
        abbrev = 'o',
        help = "Output directory for generated source code (if not set will be written to stdout).",
        category = "io",
        defaultValue = ""
    )
    var outputDirectory: String = "",

    @JvmField
    @Option(
        name = "test-output",
        help = "Output directory for generated test code (if not set test code will be omitted).",
        category = "io",
        defaultValue = ""
    )
    var testOutputDirectory: String = "",

    @JvmField
    @Option(
        name = "source",
        help = "Source directory (proto files). This option is deprecated and will be removed once the configuration process is migrated to use proto annotations.",
        category = "io",
        defaultValue = ""
    )
    var sourceDirectory: String = "",

    @JvmField
    @Option(
        name = "fallback",
        help = "Use gRPC fallback. This option is not yet implemented.",
        defaultValue = "false"
    )
    var fallback: Boolean = false,

    @JvmField
    @Option(
        name = "lite",
        help = "Use protobuf lite (recommended for Android).",
        defaultValue = "false"
    )
    var lite: Boolean = false,

    @JvmField
    @Option(
        name = "auth-google-cloud",
        help = "Add additional methods to support authentication on Google Cloud.",
        defaultValue = "false"
    )
    var authGoogleCloud: Boolean = false,

    @JvmField
    @Option(
        name = "include-google-common",
        help = "Well known Google types will be ignored if they are found in the input. This is normally useful to prevent well known types from being generated multiples times, but it can be disabled with this flag.",
        defaultValue = "false"
    )
    var includeGoogleCommon: Boolean = false
) : OptionsBase()
