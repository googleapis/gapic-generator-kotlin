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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

/**
 * Declarations for well known google types so we don't have to depend on them.
 *
 * @author jbolinger
 */
internal interface GrpcTypes {

    // auth type
    interface Auth {
        companion object {
            val MoreCallCredentials = ClassName("io.grpc.auth", "MoreCallCredentials")
            val GoogleCredentials = ClassName("com.google.auth.oauth2", "GoogleCredentials")
            val AccessToken = ClassName("com.google.auth.oauth2", "AccessToken")
        }
    }

    // kgax types
    interface Support {
        companion object {
            const val SUPPORT_LIB_PACKAGE = "com.google.kgax"
            const val SUPPORT_LIB_GRPC_PACKAGE = "$SUPPORT_LIB_PACKAGE.grpc"

            val ProtoBuilder = ClassName(SUPPORT_LIB_PACKAGE, "ProtoBuilder")

            fun GrpcClientStub(type: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "GrpcClientStub").parameterizedBy(type)

            val ClientCallOptions = ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientCallOptions")
            val ClientCallOptionsBuilder =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientCallOptions.Builder")

            fun FutureCall(type: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "FutureCall").parameterizedBy(type)

            fun CallResult(type: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "CallResult").parameterizedBy(type)

            fun StreamingCall(requestType: TypeName, responseType: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "StreamingCall").parameterizedBy(
                    requestType,
                    responseType
                )

            fun RequestStream(type: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "RequestStream").parameterizedBy(type)

            fun ClientStreamingCall(requestType: TypeName, responseType: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ClientStreamingCall").parameterizedBy(
                    requestType,
                    responseType
                )

            fun ServerStreamingCall(type: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "ServerStreamingCall").parameterizedBy(type)

            fun LongRunningCall(type: TypeName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "LongRunningCall").parameterizedBy(type)

            fun Pager(requestTypeT: TypeName, responseType: TypeName, elementType: TypeName) =
                ClassName(SUPPORT_LIB_PACKAGE, "Pager").parameterizedBy(
                    requestTypeT,
                    responseType,
                    elementType
                )

            fun PageResult(type: ClassName) =
                ClassName(SUPPORT_LIB_GRPC_PACKAGE, "PageResult").parameterizedBy(type)
        }
    }

    // guava types
    interface Guava {
        companion object {
            fun ListenableFuture(type: TypeName) =
                ClassName("com.google.common.util.concurrent", "ListenableFuture")
                    .parameterizedBy(type)
        }
    }

    // grpc types
    companion object {
        val Channel = ClassName("io.grpc", "Channel")
        val ManagedChannel = ClassName("io.grpc", "ManagedChannel")
        val ManagedChannelBuilder = ClassName("io.grpc", "ManagedChannelBuilder")
        val CallOptions = ClassName("io.grpc", "CallOptions")
        val OperationsGrpc = ClassName("com.google.longrunning", "OperationsGrpc")
        val OperationsFutureStub =
            ClassName("com.google.longrunning.OperationsGrpc", "OperationsFutureStub")
        val ByteString = ClassName("com.google.protobuf", "ByteString")
        val MethodDescriptorType = ClassName("io.grpc", "MethodDescriptor.MethodType")
        val ProtoLiteUtils = ClassName("io.grpc.protobuf.lite", "ProtoLiteUtils")

        fun AbstractStub(type: TypeName) = ClassName("io.grpc.stub", "AbstractStub")
            .parameterizedBy(type)

        fun MethodDescriptor(requestType: TypeName, responseType: TypeName) =
            ClassName("io.grpc", "MethodDescriptor").parameterizedBy(
                requestType,
                responseType
            )

        fun StreamObserver(type: TypeName) =
            ClassName("io.grpc.stub", "StreamObserver").parameterizedBy(type)
    }
}
