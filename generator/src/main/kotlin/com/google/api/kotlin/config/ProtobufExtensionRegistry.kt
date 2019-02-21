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

import com.google.api.AnnotationsProto
import com.google.api.ClientProto
import com.google.longrunning.OperationsProto
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.GeneratedMessage

/** Extension registry to use for all parsing */
internal object ProtobufExtensionRegistry {

    val INSTANCE: ExtensionRegistry by lazy {
        val extensionRegistry = ExtensionRegistry.newInstance()
        AnnotationsProto.registerAllExtensions(extensionRegistry)
        ClientProto.registerAllExtensions(extensionRegistry)
        OperationsProto.registerAllExtensions(extensionRegistry)
        extensionRegistry
    }
}

internal fun <T> DescriptorProtos.FileOptions.getExtensionOrNull(
    extension: GeneratedMessage.GeneratedExtension<DescriptorProtos.FileOptions, T>
) = if (extension.isRepeated || this.hasExtension(extension)) {
    this.getExtension(extension)
} else {
    null
}

internal fun <T> DescriptorProtos.ServiceOptions.getExtensionOrNull(
    extension: GeneratedMessage.GeneratedExtension<DescriptorProtos.ServiceOptions, T>
) = if (extension.isRepeated || this.hasExtension(extension)) {
    this.getExtension(extension)
} else {
    null
}

internal fun <T> DescriptorProtos.MethodOptions.getExtensionOrNull(
    extension: GeneratedMessage.GeneratedExtension<DescriptorProtos.MethodOptions, T>
) = if (extension.isRepeated || this.hasExtension(extension)) {
    this.getExtension(extension)
} else {
    null
}
