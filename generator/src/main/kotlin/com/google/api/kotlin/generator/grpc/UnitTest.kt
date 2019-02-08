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
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.PropertyPath
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.types.GrpcTypes
import com.google.api.kotlin.util.FieldNamer
import com.google.api.kotlin.util.Flattening
import com.google.api.kotlin.util.ParameterInfo
import com.google.api.kotlin.util.ProtoFieldInfo
import com.google.api.kotlin.util.describeMap
import com.google.api.kotlin.util.isMap
import com.google.api.kotlin.util.isRepeated
import com.google.protobuf.DescriptorProtos
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/** Generates the unit tests for the client methods. */
internal interface UnitTest {

    fun generate(context: GeneratorContext, apiMethods: List<TestableFunSpec>): GeneratedSource?

    /**
     * Create a unit test for a unary method with variations for paging, flattening,
     * and long running operations.
     */
    fun createUnaryMethodUnitTest(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodOptions: MethodOptions,
        parameters: List<ParameterInfo>,
        flatteningConfig: FlattenedMethod?
    ): CodeBlock

    /**
     * Create a unit test for a client, server, or bi-directional streaming method
     * with variations for paging, flattening, and long running operations.
     */
    fun createStreamingMethodTest(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodOptions: MethodOptions,
        parameters: List<ParameterInfo>,
        flatteningConfig: FlattenedMethod?
    ): CodeBlock

    companion object {
        const val FUN_GET_CLIENT = "getClient"

        const val MOCK_API_STUB = "apiStub"
        const val MOCK_OPS_STUB = "operationsStub"
        const val MOCK_CHANNEL = "channel"
        const val MOCK_CALL_OPTS = "options"
    }
}

internal class UnitTestImpl(
    private val stubs: Stubs,
    private val mockMaker: MockMaker = DefaultMockMaker
) : UnitTest {

    override fun generate(
        context: GeneratorContext,
        apiMethods: List<TestableFunSpec>
    ): GeneratedSource? {
        val name = "${context.className.simpleName}Test"
        val unitTestType = TypeSpec.classBuilder(name)

        // add props (mocks) that will be used by all test methods
        val mocks = mapOf(
            UnitTest.MOCK_API_STUB to stubs.getApiStubType(context),
            UnitTest.MOCK_OPS_STUB to stubs.getOperationsStubType(context),
            UnitTest.MOCK_CHANNEL to GrpcTypes.ManagedChannel,
            UnitTest.MOCK_CALL_OPTS to GrpcTypes.Support.ClientCallOptions
        )
        for ((propName, type) in mocks) {
            unitTestType.addProperty(
                PropertySpec.builder(propName, type)
                    .initializer("mock()")
                    .build()
            )
        }

        // add a function to reset the mocks before each test
        unitTestType.addFunction(
            FunSpec.builder("resetMocks")
                .addAnnotation(ClassName("kotlin.test", "BeforeTest"))
                .addStatement(
                    "reset(%N, %N, %N, %N)", *mocks.keys.toTypedArray()
                )
                .build()
        )

        // add a function to create a client for each test
        unitTestType.addFunction(
            FunSpec.builder(UnitTest.FUN_GET_CLIENT)
                .returns(context.className)
                .addStatement(
                    """
                    |return %T.create(
                    |    channel = ${UnitTest.MOCK_CHANNEL},
                    |    options = ${UnitTest.MOCK_CALL_OPTS},
                    |    factory = object:·%T.%L.Factory·{
                    |    override fun create(channel: %T, options: %T)·=
                    |        %T.%L(%N, %N)
                    |})
                    |""".trimMargin(),
                    context.className,
                    context.className, Stubs.CLASS_STUBS,
                    GrpcTypes.ManagedChannel, GrpcTypes.Support.ClientCallOptions,
                    context.className, Stubs.CLASS_STUBS,
                    UnitTest.MOCK_API_STUB, UnitTest.MOCK_OPS_STUB
                )
                .build()
        )

        // add all of the test methods for the API
        unitTestType.addFunctions(generateFunctions(apiMethods))

        // put it all together and add static imports
        return if (unitTestType.funSpecs.isNotEmpty()) {
            GeneratedSource(
                context.className.packageName,
                name,
                types = listOf(unitTestType.build()),
                imports = listOf(
                    ClassName("kotlin.test", "assertEquals"),
                    ClassName("kotlin.test", "assertNotNull"),
                    ClassName("kotlinx.coroutines", "runBlocking"),
                    ClassName("com.nhaarman.mockito_kotlin", "reset"),
                    ClassName("com.nhaarman.mockito_kotlin", "whenever"),
                    ClassName("com.nhaarman.mockito_kotlin", "doReturn"),
                    ClassName("com.nhaarman.mockito_kotlin", "doAnswer"),
                    ClassName("com.nhaarman.mockito_kotlin", "mock"),
                    ClassName("com.nhaarman.mockito_kotlin", "verify"),
                    ClassName("com.nhaarman.mockito_kotlin", "times"),
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
            .asSequence()
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
            .toList()
    }

    override fun createUnaryMethodUnitTest(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodOptions: MethodOptions,
        parameters: List<ParameterInfo>,
        flatteningConfig: FlattenedMethod?
    ): CodeBlock {
        val name = methodOptions.name.decapitalize()
        val originalReturnType = context.typeMap.getKotlinType(method.outputType)
        val originalInputType = context.typeMap.getKotlinType(method.inputType)

        // create mocks for input params and the future returned by the stub
        val givenBlock = createGivenCodeBlock(context, parameters)
        givenBlock.code.add(
            """
           |val callResult = %T(%T.newBuilder().build(), mock())
           |whenever(%N.execute<%T>(any(), any())).thenReturn(callResult)
           |""".trimMargin(),
            GrpcTypes.Support.CallResult, originalReturnType,
            UnitTest.MOCK_API_STUB, originalReturnType
        )

        // if paging add extra mocks for the page handling
        if (methodOptions.pagedResponse != null) {
            val pageSizeSetter = FieldNamer.getJavaBuilderRawSetterName(methodOptions.pagedResponse.pageSize)

            // non-paged flattened methods need an extra mock since the original
            // request object is not directly used (it's builder is used instead)
            if (flatteningConfig == null) {
                val theRequest = givenBlock.variables.values.map { it.variableName }.first()
                givenBlock.code.add(
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

        // invoke client
        val responseParameterName = if (methodOptions.pagedResponse != null) "page" else "result"
        val whenBlock = createWhenCodeBlock(
            given = givenBlock,
            methodName = name,
            parameters = parameters,
            responseParameterName = responseParameterName,
            isPager = methodOptions.pagedResponse != null
        )

        // verify the returned values
        val thenBlock = ThenCodeBlock()
        val check =
            createStubCheckCode(givenBlock, context, method, flatteningConfig)

        // verify the executeFuture occurred (and use input block to verify)
        thenBlock.code.add(
            """
            |verify(%N).execute<%T>(any(), check·{
            |    val mock: %T = mock()
            |    it(mock)
            |    verify(mock).%N(%L)
            |})
            |""".trimMargin(),
            UnitTest.MOCK_API_STUB, originalReturnType,
            stubs.getApiStubType(context).typeArguments.first(),
            name, check
        )

        // put it all together
        return createUnitTest(givenBlock, whenBlock, thenBlock)
    }

    override fun createStreamingMethodTest(
        context: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        methodOptions: MethodOptions,
        parameters: List<ParameterInfo>,
        flatteningConfig: FlattenedMethod?
    ): CodeBlock {
        val name = methodOptions.name.decapitalize()
        val originalReturnType = context.typeMap.getKotlinType(method.outputType)
        val originalInputType = context.typeMap.getKotlinType(method.inputType)

        // create mocks for input params and the future returned by the stub
        val givenBlock = createGivenCodeBlock(context, parameters)

        // determine method that will be used and it's return type
        val streamMethod = when {
            method.hasClientStreaming() && !method.hasServerStreaming() ->
                CodeBlock.of(
                    "executeClientStreaming<%T, %T>",
                    originalInputType, originalReturnType
                )
            method.hasServerStreaming() && !method.hasClientStreaming() ->
                CodeBlock.of(
                    "executeServerStreaming<%T>",
                    originalReturnType
                )
            else -> CodeBlock.of(
                "executeStreaming<%T, %T>",
                originalInputType, originalReturnType
            )
        }
        val streamMethodReturnType = when {
            method.hasClientStreaming() && !method.hasServerStreaming() ->
                GrpcTypes.Support.ClientStreamingCall(originalInputType, originalReturnType)
            method.hasServerStreaming() && !method.hasClientStreaming() ->
                GrpcTypes.Support.ServerStreamingCall(originalReturnType)
            else -> GrpcTypes.Support.StreamingCall(originalInputType, originalReturnType)
        }

        // add a mock for the streaming call
        // if flattened we need to check the outbound request stream
        givenBlock.code.addStatement("val streaming: %T = mock()", streamMethodReturnType)
        if (flatteningConfig != null && method.hasClientStreaming()) {
            givenBlock.code.addStatement(
                "whenever(%N.prepare(any<%T.() -> Unit>())).thenReturn(%N)",
                UnitTest.MOCK_API_STUB,
                GrpcTypes.Support.ClientCallOptionsBuilder,
                UnitTest.MOCK_API_STUB
            )
        }
        givenBlock.code.addStatement(
            "whenever(%N.%L(any(), any())).thenReturn(streaming)",
            UnitTest.MOCK_API_STUB,
            streamMethod
        )

        // invoke client
        val whenBlock = createWhenCodeBlock(
            given = givenBlock,
            methodName = name,
            parameters = parameters,
            responseParameterName = "result",
            isPager = false
        )

        // verify the returned values
        val thenBlock = ThenCodeBlock()

        // verify the executeFuture occurred (and use input block to verify)
        if (method.hasServerStreaming() && !method.hasClientStreaming()) {
            val check = createStubCheckCode(givenBlock, context, method, flatteningConfig)
            thenBlock.code.add(
                """
                |verify(%N).%L(any(), check·{
                |    val mock: %T = mock()
                |    val mockObserver: %T = mock()
                |    it(mock, mockObserver)
                |    verify(mock).%L(%L, eq(mockObserver))
                |})
                |""".trimMargin(),
                UnitTest.MOCK_API_STUB, streamMethod,
                stubs.getApiStubType(context).typeArguments.first(),
                GrpcTypes.StreamObserver(originalReturnType),
                name, check
            )
        } else {
            thenBlock.code.add(
                """
                |verify(%N).%L(any(), check·{
                |    val mock: %T = mock()
                |    assertEquals(mock::%L, it(mock))
                |})
                |""".trimMargin(),
                UnitTest.MOCK_API_STUB, streamMethod,
                stubs.getApiStubType(context).typeArguments.first(),
                name
            )
        }

        // if flattening was used also verify that the args were sent
        if (flatteningConfig != null && method.hasClientStreaming()) {
            val checks =
                createNestedAssertCodeForStubCheck(givenBlock, context, method, flatteningConfig)
            thenBlock.code.add(
                """
                |verify(%N).prepare(check<%T.()·->·Unit>·{
                |    val options = %T().apply(it).build()
                |    options.initialRequests.map·{ it as %T }.first().let·{
                |${checks.joinToString("\n") { "        %L" }}
                |    }
                |    assertEquals(options.initialRequests.size, 1)
                |})
                |""".trimMargin(),
                UnitTest.MOCK_API_STUB,
                GrpcTypes.Support.ClientCallOptionsBuilder,
                GrpcTypes.Support.ClientCallOptionsBuilder,
                originalInputType,
                *checks.toTypedArray()
            )
        }

        // put it all together
        return createUnitTest(givenBlock, whenBlock, thenBlock)
    }

    // common code for setting up mock inputs for unary and streaming methods
    private fun createGivenCodeBlock(
        ctx: GeneratorContext,
        parameters: List<ParameterInfo>
    ): GivenCodeBlock {
        // create a mock for each input parameter
        val variables = mapOf(*parameters.map {
            val valName = "the${it.spec.name.capitalize()}"
            val init = CodeBlock.of(
                "val $valName: %T = %L",
                it.spec.type,
                mockMaker.getMockValue(ctx.typeMap, it.flattenedFieldInfo)
            )
            Pair(it.spec.name, UnitTestVariable(valName, init))
        }.toTypedArray())

        // generate code for each of the given mocks
        val code = CodeBlock.builder()
        for ((_, value) in variables.entries) {
            code.addStatement("%L", value.initializer)
        }

        return GivenCodeBlock(variables, code)
    }

    // common code for invoking the client with mocked input parameters
    private fun createWhenCodeBlock(
        given: GivenCodeBlock,
        methodName: String,
        parameters: List<ParameterInfo>,
        responseParameterName: String,
        isPager: Boolean
    ): WhenCodeBlock {
        // invoke call to client using mocks
        val invokeClientParams = parameters.map {
            given.variables[it.spec.name]?.variableName
                ?: throw IllegalStateException("unable to determine variable name: ${it.spec.name}")
        }

        // create and call client with all parameters
        // add a call to next for paged responses so we don't end up with the pager object
        val params = invokeClientParams.joinToString(", ") { "%N" }
        val extra = if (isPager) ".receive()" else ""
        val testWhen = CodeBlock.builder().add(
            """
            |val client = %N()
            |val %L = client.%N($params)$extra
            |
            |assertNotNull(%L)
            """.trimMargin(),
            UnitTest.FUN_GET_CLIENT,
            responseParameterName, methodName, *invokeClientParams.toTypedArray(),
            responseParameterName
        )

        return WhenCodeBlock(testWhen)
    }

    // common code for checking that a stub was invoked with correct params
    private fun createStubCheckCode(
        given: GivenCodeBlock,
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        flatteningConfig: FlattenedMethod?
    ): CodeBlock {
        val check = CodeBlock.builder()
        if (flatteningConfig == null) {
            check.add("eq(%N)", given.variables.values.map { it.variableName }.first())
        } else {
            // get an assert for each parameter
            val nestedAssert =
                createNestedAssertCodeForStubCheck(given, ctx, method, flatteningConfig)

            // put it all together in a check block
            check.add(
                """
                |check·{
                |${nestedAssert.joinToString("\n") { "    %L" }}
                |}""".trimMargin(), *nestedAssert.toTypedArray()
            )
        }

        return check.build()
    }

    // get a list of assertEquals for a flattened method
    private fun createNestedAssertCodeForStubCheck(
        given: GivenCodeBlock,
        ctx: GeneratorContext,
        method: DescriptorProtos.MethodDescriptorProto,
        flatteningConfig: FlattenedMethod
    ): MutableList<CodeBlock> {
        val nestedAsserts = mutableListOf<CodeBlock>()

        Flattening.visitFlattenedMethod(
            ctx,
            method,
            flatteningConfig.parameters,
            object : Flattening.Visitor() {
                override fun onTerminalParam(
                    currentPath: PropertyPath,
                    fieldInfo: ProtoFieldInfo
                ) {
                    val key = FieldNamer.getFieldName(currentPath.lastSegment)
                    val accessor = FieldNamer.getJavaAccessorName(ctx.typeMap, fieldInfo)
                    val variable = given.variables[key]?.variableName
                        ?: throw IllegalStateException("Could not locate variable with name: $key")

                    nestedAsserts.add(CodeBlock.of("assertEquals($variable, it.$accessor)"))
                }
            })

        return nestedAsserts
    }

    // merge all the code blocks into a single block
    private fun createUnitTest(
        givenBlock: GivenCodeBlock,
        whenBlock: WhenCodeBlock,
        thenBlock: ThenCodeBlock
    ) =
        CodeBlock.of(
            """
            |return runBlocking<Unit>·{
            |    %L
            |    %L
            |    %L
            |}""".trimMargin(),
            givenBlock.code.build(),
            whenBlock.code.build(),
            thenBlock.code.build()
        )

    /** Simple deterministic mock maker */
    internal object DefaultMockMaker : MockMaker {

        override fun getMockValue(typeMap: ProtobufTypeMapper, type: ProtoFieldInfo?): String {
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
}

/** Gets a value when required (primitives) or a mock */
internal interface MockMaker {
    fun getMockValue(typeMap: ProtobufTypeMapper, type: ProtoFieldInfo?): String
}

private class GivenCodeBlock(
    val variables: Map<String, UnitTestVariable>,
    val code: CodeBlock.Builder
)

private class WhenCodeBlock(val code: CodeBlock.Builder = CodeBlock.Builder())
private class ThenCodeBlock(val code: CodeBlock.Builder = CodeBlock.builder())
private class UnitTestVariable(val variableName: String, val initializer: CodeBlock)
