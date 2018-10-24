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

package com.google.api.kotlin.generator.grpc

import com.google.api.kotlin.GeneratedSource
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.types.GrpcTypes
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

/**
 * Generates the base class for the main client that contains code
 * that should rarely be viewed by client users.
 */
internal interface BaseClass {
    fun generate(context: GeneratorContext, by: AnnotationSpec): GeneratedSource
    fun typeName(context: GeneratorContext): TypeName
    fun stubTypeName(context: GeneratorContext): TypeName

    companion object {
        const val PARAM_REQUEST = "request"
        const val PARAM_RESPONSE_OBSERVER = "responseObserver"
    }
}

internal class BaseClassImpl : BaseClass {

    override fun typeName(context: GeneratorContext) =
        ClassName(context.className.packageName, "${context.className.simpleName}Base")

    override fun stubTypeName(context: GeneratorContext) =
        ClassName("${context.className.packageName}.${context.className.simpleName}Base", "Stub")

    override fun generate(context: GeneratorContext, by: AnnotationSpec): GeneratedSource {
        // create type
        val type = createType(context, by)

        // add static imports
        val imports = listOf(
            ClassName("io.grpc.MethodDescriptor", "generateFullMethodName"),
            ClassName("io.grpc.stub.ClientCalls", "futureUnaryCall"),
            ClassName("io.grpc.stub.ClientCalls", "asyncBidiStreamingCall"),
            ClassName("io.grpc.stub.ClientCalls", "asyncClientStreamingCall"),
            ClassName("io.grpc.stub.ClientCalls", "asyncServerStreamingCall"),
            GrpcTypes.StatusCode
        )

        // put it all together
        return GeneratedSource(
            context.className.packageName,
            typeName(context).simpleName,
            types = listOf(type),
            imports = imports
        )
    }

    // creates the base class type
    private fun createType(context: GeneratorContext, by: AnnotationSpec): TypeSpec {
        val type = TypeSpec.classBuilder(typeName(context))
            .addModifiers(KModifier.ABSTRACT)
            .addAnnotation(by)

        // add constructor
        type.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(Properties.PROP_CHANNEL, GrpcTypes.ManagedChannel)
                .addParameter(Properties.PROP_CALL_OPTS, GrpcTypes.Support.ClientCallOptions)
                .build()
        )

        // add properties
        type.addProperty(
            PropertySpec.builder(Properties.PROP_CHANNEL, GrpcTypes.ManagedChannel)
                .initializer("%L", Properties.PROP_CHANNEL)
                .build()
        )
        type.addProperty(
            PropertySpec.builder(Properties.PROP_CALL_OPTS, GrpcTypes.Support.ClientCallOptions)
                .initializer("%L", Properties.PROP_CALL_OPTS)
                .build()
        )

        // generate a method descriptor for each method
        type.addProperties(context.service.methodList.map {
            createMethodDescriptor(context, it)
        })

        // add the nested stub class
        type.addType(createNestedStubType(context))

        return type.build()
    }

    // creates a method descriptor
    private fun createMethodDescriptor(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto
    ): PropertySpec {
        val inputType = context.typeMap.getKotlinType(method.inputType)
        val outputType = context.typeMap.getKotlinType(method.outputType)
        val type = GrpcTypes.MethodDescriptor(inputType, outputType)

        // create the property
        val prop = PropertySpec.builder(descriptorPropertyName(method), type)
            .addModifiers(KModifier.PROTECTED)

        val methodType = when {
            method.hasServerStreaming() && method.hasClientStreaming() -> "BIDI_STREAMING"
            method.hasClientStreaming() -> "CLIENT_STREAMING"
            method.hasServerStreaming() -> "SERVER_STREAMING"
            else -> "UNARY"
        }

        // create the initializer
        val init = CodeBlock.of(
            """
            |%T.newBuilder<%T, %T>()
            |    .setType(%T.%L)
            |    .setFullMethodName(generateFullMethodName(%S, %S))
            |    .setSampledToLocalTracing(true)
            |    .setRequestMarshaller(%T.marshaller(
            |        %T.getDefaultInstance()))
            |    .setResponseMarshaller(%T.marshaller(
            |        %T.getDefaultInstance()))
            |    .build()
            |""".trimMargin(),
            type.rawType, inputType, outputType,
            GrpcTypes.MethodDescriptorType, methodType,
            "${context.proto.`package`}.${context.service.name}", method.name,
            GrpcTypes.ProtoLiteUtils,
            inputType,
            GrpcTypes.ProtoLiteUtils,
            outputType
        )

        // wrap it in a lazy delegate
        prop.delegate(
            """
            |lazy {
            |%L
            |}""".trimMargin(),
            init
        )

        return prop.build()
    }

    // creates the nested stub type
    private fun createNestedStubType(context: GeneratorContext): TypeSpec {
        val className = stubTypeName(context)
        val type = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.INNER)
            .superclass(GrpcTypes.AbstractStub(className))
            .addSuperclassConstructorParameter("channel", GrpcTypes.Channel)
            .addSuperclassConstructorParameter("callOptions", GrpcTypes.CallOptions)

        // and constructor
        type.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("channel", GrpcTypes.Channel)
                .addParameter(
                    ParameterSpec.builder("callOptions", GrpcTypes.CallOptions)
                        .defaultValue("%T.DEFAULT", GrpcTypes.CallOptions)
                        .build()
                )
                .build()
        )

        // add the build method from the superclass
        type.addFunction(
            FunSpec.builder("build")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("channel", GrpcTypes.Channel)
                .addParameter("callOptions", GrpcTypes.CallOptions)
                .returns(className)
                .addStatement("return %T(channel, callOptions)", className)
                .build()
        )

        // add stub methods
        type.addFunctions(context.service.methodList.map { createNestedStubMethod(context, it) })

        return type.build()
    }

    // create the API method for the stub type
    private fun createNestedStubMethod(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto
    ): FunSpec {
        val func = FunSpec.builder(method.name.decapitalize())

        val inputType = context.typeMap.getKotlinType(method.inputType)
        val outputType = context.typeMap.getKotlinType(method.outputType)

        // set return (if needed)
        val returnType = when {
            method.hasClientStreaming() -> GrpcTypes.StreamObserver(inputType)
            method.hasServerStreaming() -> null
            else -> GrpcTypes.Guava.ListenableFuture(outputType)
        }
        returnType?.let { func.returns(it) }

        // add parameters and build method body
        when {
            method.hasServerStreaming() && method.hasClientStreaming() -> {
                func.addParameter(
                    BaseClass.PARAM_RESPONSE_OBSERVER, GrpcTypes.StreamObserver(outputType)
                )
                func.addStatement(
                    """
                    |return asyncBidiStreamingCall(
                    |    channel.newCall(%L, callOptions),
                    |    %L
                    |)
                    """.trimMargin(),
                    descriptorPropertyName(method),
                    BaseClass.PARAM_RESPONSE_OBSERVER
                )
            }
            method.hasServerStreaming() -> {
                func.addParameter(BaseClass.PARAM_REQUEST, inputType)
                func.addParameter(
                    BaseClass.PARAM_RESPONSE_OBSERVER, GrpcTypes.StreamObserver(outputType)
                )
                func.addStatement(
                    """
                    |return asyncServerStreamingCall(
                    |    channel.newCall(%L, callOptions),
                    |    %L,
                    |    %L
                    |)
                    """.trimMargin(),
                    descriptorPropertyName(method),
                    BaseClass.PARAM_REQUEST,
                    BaseClass.PARAM_RESPONSE_OBSERVER
                )
            }
            method.hasClientStreaming() -> {
                func.addParameter(
                    BaseClass.PARAM_RESPONSE_OBSERVER, GrpcTypes.StreamObserver(outputType)
                )
                func.addStatement(
                    """
                    |return asyncClientStreamingCall(
                    |    channel.newCall(%L, callOptions),
                    |    %L
                    |)
                    """.trimMargin(),
                    descriptorPropertyName(method),
                    BaseClass.PARAM_RESPONSE_OBSERVER
                )
            }
            else -> {
                func.addParameter(BaseClass.PARAM_REQUEST, inputType)
                func.addStatement(
                    """
                    |return futureUnaryCall(
                    |    channel.newCall(%L, callOptions),
                    |    %L
                    |)
                    |""".trimMargin(),
                    descriptorPropertyName(method),
                    BaseClass.PARAM_REQUEST
                )
            }
        }

        return func.build()
    }

    private fun descriptorPropertyName(method: DescriptorProtos.MethodDescriptorProto) =
        "${method.name.decapitalize()}Descriptor"
}
