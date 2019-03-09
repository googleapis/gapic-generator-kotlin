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

package com.google.api.kotlin.types

import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName

internal interface ProtoTypes {
    companion object {
        val GOOGLE_BYTESTREAM = "google/bytestream/bytestream.proto"
        val GOOGLE_OPERATIONS = "google/longrunning/operations.proto"

        val EMPTY = ClassName("com.google.protobuf", "Empty")
    }
}

// protos to ignore if found during processing
private val SKIP_PROTOS = listOf(
    ProtoTypes.GOOGLE_BYTESTREAM,
    ProtoTypes.GOOGLE_OPERATIONS
)

internal fun DescriptorProtos.FileDescriptorProto.isWellKnown() = SKIP_PROTOS.contains(this.name)
internal fun DescriptorProtos.FileDescriptorProto.isNotWellKnown() = !this.isWellKnown()

internal fun DescriptorProtos.FileDescriptorProto.isGoogleOperationsProto() = ProtoTypes.GOOGLE_OPERATIONS == this.name

internal fun ClassName.isProtobufEmpty() = ProtoTypes.EMPTY == this
internal fun ClassName.isNotProtobufEmpty() = !this.isProtobufEmpty()