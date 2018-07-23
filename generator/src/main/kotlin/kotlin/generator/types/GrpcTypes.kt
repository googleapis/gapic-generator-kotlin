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

package com.google.api.kotlin.generator.types

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName

/**
 * Declarations for well known google types so we don't have to depend on them.
 *
 * @author jbolinger
 */
internal interface GrpcTypes {

    interface Auth {
        companion object {
            val MoreCallCredentials = ClassName("io.grpc.auth", "MoreCallCredentials")
            val GoogleCredentials = ClassName("com.google.auth.oauth2", "GoogleCredentials")
            val AccessToken = ClassName("com.google.auth.oauth2", "AccessToken")
        }
    }

    interface Support {
        companion object {
            const val SUPPORT_LIB_PACKAGE = "com.google.kgax"
            const val SUPPORT_LIB_GRPC_PACKAGE = "$SUPPORT_LIB_PACKAGE.grpc"

            val GrpcClient = ClassName(SUPPORT_LIB_GRPC_PACKAGE, "GrpcClient")

            fun ClientCall(type: ClassName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientCall"), type)
            val ClientCallOptions = ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientCallOptions")
            val ClientCallOptionsBuilder = ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientCallOptions.Builder")

            fun FutureCall(type: ClassName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "FutureCall"), type)
            fun CallResult(type: ClassName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "CallResult"), type)
            fun StreamingCall(requestType: ClassName, responseType: ClassName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "StreamingCall"), requestType, responseType)
            fun ClientStreamingCall(requestType: ClassName, responseType: ClassName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientStreamingCall"), requestType, responseType)
            fun ServerStreamingCall(type: ClassName) =
                    ParameterizedTypeName.get(
                            ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ServerStreamingCall"), type)

            fun LongRunningCall(type: TypeName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "LongRunningCall"), type)

            fun Pager(requestTypeT: TypeName, responseType: TypeName, elementType: TypeName) =
                    ParameterizedTypeName.get(ClassName(SUPPORT_LIB_PACKAGE, "Pager"),
                            requestTypeT, responseType, elementType)
            fun PageResult(type: ClassName) = ParameterizedTypeName.get(
                    ClassName(SUPPORT_LIB_GRPC_PACKAGE, "PageResult"), type)
        }
    }

    companion object {
        val ManagedChannel = ClassName("io.grpc", "ManagedChannel")
        val OkHttpChannelBuilder = ClassName("io.grpc.okhttp", "OkHttpChannelBuilder")
        val OperationsGrpc = ClassName("com.google.longrunning", "OperationsGrpc")
        val OperationsFutureStub = ClassName("com.google.longrunning", "OperationsGrpc.OperationsFutureStub")
        val ByteString = ClassName("com.google.protobuf", "ByteString")
    }

}
