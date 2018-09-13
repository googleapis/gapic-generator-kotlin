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

package com.google.api.kotlin.util

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.asPropertyPath
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName

internal object ResponseTypes {

    /** Get the real response type for an LRO operation */
    fun getLongRunningResponseType(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto
    ): ClassName {
        // TODO: there is no guarantee that this will always hold,
        //       but there isn't any more info in the proto (yet)
        val name = method.inputType.replace("Request\\z".toRegex(), "Response")
        if (name == method.inputType) throw IllegalStateException("Unable to determine Operation response type")
        return ctx.typeMap.getKotlinType(name)
    }

    /** Get the type of element in a paged result list */
    fun getResponseListElementType(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        paging: PagedResponse
    ): ClassName {
        val outputType = ctx.typeMap.getProtoTypeDescriptor(method.outputType)
        val info = getProtoFieldInfoForPath(ctx, paging.responseList.asPropertyPath(), outputType)
        return info.field.asClassName(ctx.typeMap)
    }
}