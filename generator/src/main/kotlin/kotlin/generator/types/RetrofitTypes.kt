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

package com.google.api.experimental.kotlin.generator.types

import com.squareup.kotlinpoet.ClassName

/**
 * Declarations for well known google types so we don't have to depend on them.
 *
 * @author jbolinger
 */
internal interface RetrofitTypes {

    companion object {
        val OkHttpClient = ClassName("okhttp3", "OkHttpClient")
        val Request = ClassName("okhttp3", "Request")
        val Call = ClassName("retrofit2", "Call")
        val Retrofit = ClassName("retrofit2", "Retrofit")
        val ProtoConverterFactory = ClassName("retrofit2.converter.protobuf", "ProtoConverterFactory")
        val Body = ClassName("retrofit2.http", "Body")
        val POST = ClassName("retrofit2.http", "POST")
    }
}