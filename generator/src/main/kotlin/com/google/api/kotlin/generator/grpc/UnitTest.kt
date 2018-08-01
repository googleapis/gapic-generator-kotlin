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
import com.google.api.kotlin.TestableFunSpec
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.PagedResponse
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.generator.AbstractGenerator
import com.google.api.kotlin.generator.ProtoFieldInfo
import com.google.api.kotlin.generator.describeMap
import com.google.api.kotlin.generator.isLongRunningOperation
import com.google.api.kotlin.generator.isMap
import com.google.api.kotlin.generator.isRepeated
import com.google.api.kotlin.types.GrpcTypes
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal class UnitTest(val stubs: Stubs) : AbstractGenerator() {

    companion object {
        const val FUN_GET_CLIENT = "getClient"

        const val MOCK_STREAM_STUB = "streamingStub"
        const val MOCK_FUTURE_STUB = "futureStub"
        const val MOCK_OPS_STUB = "operationsStub"
        const val MOCK_CHANNEL = "channel"
        const val MOCK_CALL_OPTS = "options"
    }

    fun generate(ctx: GeneratorContext, apiMethods: List<TestableFunSpec>): GeneratedSource? {
        val name = "${ctx.className.simpleName}Test"
        val unitTestType = TypeSpec.classBuilder(name)

        // add props (mocks)
        val mocks = mapOf(
            MOCK_STREAM_STUB to stubs.getStreamStubType(ctx),
            MOCK_FUTURE_STUB to stubs.getFutureStubType(ctx),
            MOCK_OPS_STUB to stubs.getOperationsStubType(ctx),
            MOCK_CHANNEL to GrpcTypes.ManagedChannel,
            MOCK_CALL_OPTS to GrpcTypes.Support.ClientCallOptions
        )
        for ((propName, type) in mocks) {
            unitTestType.addProperty(
                PropertySpec.builder(propName, type)
                    .initializer("mock()")
                    .build()
            )
        }

        // add functions
        unitTestType.addFunction(
            FunSpec.builder("resetMocks")
                .addAnnotation(ClassName("kotlin.test", "BeforeTest"))
                .addStatement(
                    "reset(%N, %N, %N, %N, %N)", *mocks.keys.toTypedArray()
                )
                .build()
        )
        unitTestType.addFunction(
            FunSpec.builder(FUN_GET_CLIENT)
                .returns(ctx.className)
                .addStatement(
                    """
                        |return %T.fromStubs(object: %T.%L.Factory {
                        |    override fun create(channel: %T, options: %T) =
                        |        %T.%L(%N, %N, %N)
                        |}, %N, %N)
                        |""".trimMargin(),
                    ctx.className, ctx.className,
                    Stubs.CLASS_NAME,
                    GrpcTypes.ManagedChannel, GrpcTypes.Support.ClientCallOptions,
                    ctx.className,
                    Stubs.CLASS_NAME,
                    MOCK_STREAM_STUB,
                    MOCK_FUTURE_STUB,
                    MOCK_OPS_STUB,
                    MOCK_CHANNEL,
                    MOCK_CALL_OPTS
                )
                .build()
        )
        unitTestType.addFunctions(generateFunctions(apiMethods))

        // put it all together
        return if (unitTestType.funSpecs.isNotEmpty()) {
            GeneratedSource(
                ctx.className.packageName,
                name,
                types = listOf(unitTestType.build()),
                imports = listOf(
                    ClassName("kotlin.test", "assertEquals"),
                    ClassName("kotlin.test", "assertNotNull"),
                    ClassName("com.nhaarman.mockito_kotlin", "reset"),
                    ClassName("com.nhaarman.mockito_kotlin", "whenever"),
                    ClassName("com.nhaarman.mockito_kotlin", "doReturn"),
                    ClassName("com.nhaarman.mockito_kotlin", "mock"),
                    ClassName("com.nhaarman.mockito_kotlin", "verify"),
                    ClassName("com.nhaarman.mockito_kotlin", "check"),
                    ClassName("com.nhaarman.mockito_kotlin", "eq"),
                    ClassName("com.nhaarman.mockito_kotlin", "any")
                ),
                kind = GeneratedSource.Kind.UNIT_TEST
            )
        } else {
            null
        }
    }

    private fun generateFunctions(functions: List<TestableFunSpec>): List<FunSpec> {
        val nameCounter = mutableMapOf<String, Int>()

        return functions
            .filter { it.unitTestCode != null }
            .map {
                // add a numbered suffix to the name if there are overloads
                var name = "test${it.function.name.capitalize()}"
                val suffix = nameCounter[name] ?: 0
                nameCounter[name] = suffix + 1
                if (suffix > 0) {
                    name += suffix
                }

                // create fun!
                FunSpec.builder(name)
                    .addAnnotation(ClassName("kotlin.test", "Test"))
                    .addCode(it.unitTestCode!!)
                    .build()
            }
    }

    fun createUnaryMethodUnitTest(
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodName: String,
        parameters: List<ParameterInfo>,
        flatteningConfig: FlattenedMethod? = null,
        paging: PagedResponse? = null
    ): CodeBlock {
        val originalReturnType = ctx.typeMap.getKotlinType(method.outputType)
        val originalInputType = ctx.typeMap.getKotlinType(method.inputType)

        // create a mock for each input parameter
        val unitTestGivenVals = mapOf(*parameters.map {
            val valName = "the${it.spec.name.capitalize()}"
            val init = CodeBlock.of(
                "val $valName: %T = %L",
                it.spec.type,
                getMockValue(ctx.typeMap, it.flattenedFieldInfo)
            )
            Pair(it.spec.name, UnitTestInfo(valName, init))
        }.toTypedArray())

        // generate code for each of the given mocks
        val unitTestGivens = CodeBlock.builder()
        for ((_, value) in unitTestGivenVals.entries) {
            unitTestGivens.addStatement("%L", value.initializer)
        }
        unitTestGivens.add(
            """
               |val future: %T = mock()
               |whenever(%N.executeFuture<%T>(any())).thenReturn(future)
               |""".trimMargin(),
            GrpcTypes.Support.FutureCall(originalReturnType),
            UnitTest.MOCK_FUTURE_STUB, originalReturnType
        )

        // if paging add extra mocks for the page handling
        if (paging != null) {
            val pageSizeSetter = getSetterName(paging.pageSize)
            val nextPageTokenGetter = getAccessorName(paging.responsePageToken)
            val responseListGetter = getAccessorRepeatedName(paging.responseList)
            val responseListItemType = getResponseListElementType(ctx, method, paging)

            unitTestGivens.add(
                """
                    |
                    |val pageBodyMock: %T = mock {
                    |    on { %L } doReturn "token"
                    |    on { %L } doReturn mock<List<%T>>()
                    |}
                    |whenever(future.get()).thenReturn(%T(pageBodyMock, mock()))
                    |""".trimMargin(),
                originalReturnType,
                nextPageTokenGetter,
                responseListGetter, responseListItemType,
                GrpcTypes.Support.CallResult(originalReturnType)
            )

            // non paged flattened methods need an extra mock since the original request object isn't
            // directly used (it's builder is used instead)
            if (flatteningConfig == null) {
                val theRequest = unitTestGivenVals.values.map { it.variableName }.first()
                unitTestGivens.add(
                    """
                        |val builder: %T.Builder = mock()
                        |whenever(%N.toBuilder()).thenReturn(builder)
                        |whenever(builder.%L(any())).thenReturn(builder)
                        |whenever(builder.build()).thenReturn(%N)
                        |""".trimMargin(),
                    originalInputType,
                    theRequest,
                    pageSizeSetter,
                    theRequest
                )
            }
        }

        // invoke call to client using mocks
        val invokeClientParams = parameters.map {
            unitTestGivenVals[it.spec.name]?.variableName
                ?: throw IllegalStateException("unable to determine variable name: ${it.spec.name}")
        }
        val unitTestWhen = CodeBlock.builder()
            .addStatement("val client = %N()", UnitTest.FUN_GET_CLIENT)
        if (paging != null) {
            unitTestWhen.addStatement(
                "val result = client.%N(${invokeClientParams.joinToString(", ") { "%N" }}, 14)",
                methodName,
                *invokeClientParams.toTypedArray()
            )
            unitTestWhen.addStatement("val page = result.next()")
        } else {
            unitTestWhen.addStatement(
                "val result = client.%N(${invokeClientParams.joinToString(", ") { "%N" }})",
                methodName,
                *invokeClientParams.toTypedArray()
            )
        }

        // verify that the inputs match
        val check = CodeBlock.builder()
        if (flatteningConfig == null) {
            check.add("eq(%N)", unitTestGivenVals.values.map { it.variableName }.first())
        } else {
            val nestedAssert = mutableListOf<CodeBlock>()
            visitFlattenedMethod(ctx, method, flatteningConfig, object : Visitor() {
                override fun onTerminalParam(
                    currentPath: List<String>,
                    fieldInfo: ProtoFieldInfo
                ) {
                    val key = getAccessorName(currentPath.last())
                    val accessor = getAccessorCode(ctx.typeMap, fieldInfo)
                    val variable = unitTestGivenVals[key]?.variableName
                        ?: throw IllegalStateException("Could not locate variable with name: $key")

                    nestedAssert.add(CodeBlock.of("assertEquals($variable, it$accessor)"))
                }
            })
            if (paging != null) {
                nestedAssert.add(CodeBlock.of("assertEquals(14, it.${getAccessorName(paging.pageSize)})"))
            }
            check.addStatement(
                """
                    |check {
                    |${nestedAssert.joinToString("\n") { "    %L" }}
                    |}""".trimMargin(), *nestedAssert.toTypedArray()
            )
        }

        // verify the returned values
        val unitTestThen = CodeBlock.builder()
        if (method.isLongRunningOperation()) {
            unitTestThen.addStatement("assertNotNull(result)")
        } else if (paging != null) {
            unitTestThen.addStatement("assertNotNull(page)")
        } else {
            unitTestThen.addStatement("assertEquals(future, result)")
        }

        // verify the executeFuture occurred (and use input block to verify)
        unitTestThen.add(
            """
                |verify(%N).executeFuture<%T>(check {
                |    val mock: %T = mock()
                |    it(mock)
                |    verify(mock).%N(%L)
                |})
                |""".trimMargin(),
            UnitTest.MOCK_FUTURE_STUB, originalReturnType,
            stubs.getFutureStubType(ctx).typeArguments.first(),
            methodName,
            check.build()
        )

        // add the page size assertion
        if (paging != null && flatteningConfig == null) {
            check.addStatement(
                "assertEquals(14, %N.%L)",
                unitTestGivenVals.values.map { it.variableName }.first(),
                getAccessorName(paging.pageSize)
            )
        }

        // put it all together in a unit test
        return CodeBlock.of(
            """
                |%L
                |%L
                |%L""".trimMargin(),
            unitTestGivens.build(),
            unitTestWhen.build(),
            unitTestThen.build()
        )
    }

    private fun getMockValue(typeMap: ProtobufTypeMapper, type: ProtoFieldInfo?): String {
        // repeated fields or unknown use a mock
        if (type == null) {
            return "mock()"
        }

        // enums must use a real value
        if (typeMap.hasProtoEnumDescriptor(type.field.typeName)) {
            val descriptor = typeMap.getProtoEnumDescriptor(type.field.typeName)
            val kotlinType = typeMap.getKotlinType(type.field.typeName)
            val enum = descriptor.valueList.firstOrNull()?.name
                ?: throw IllegalStateException("unable to find default enum value for: ${type.field.typeName}")

            return "${kotlinType.simpleName}.$enum"
        }

        // primitives must use a real value
        // TODO: better / random values?
        fun getValue(t: DescriptorProtos.FieldDescriptorProto.Type) = when (t) {
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING -> "\"hi there!\""
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL -> "true"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE -> "2.0"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT -> "4.0"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32 -> "2"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32 -> "2"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32 -> "2"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32 -> "2"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32 -> "2"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64 -> "400L"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64 -> "400L"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64 -> "400L"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64 -> "400L"
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64 -> "400L"
            else -> "mock()"
        }

        // use real lists and maps
        return when {
            type.field.isMap(typeMap) -> {
                val (keyType, valueType) = type.field.describeMap(typeMap)
                val k = getValue(keyType.type)
                val v = getValue(valueType.type)
                "mapOf($k to $v)"
            }
            type.field.isRepeated() -> "listOf(${getValue(type.field.type)})"
            else -> getValue(type.field.type)
        }
    }
}

private class UnitTestInfo(val variableName: String, val initializer: CodeBlock)
