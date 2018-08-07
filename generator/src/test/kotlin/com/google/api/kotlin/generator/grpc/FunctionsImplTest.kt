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

import com.google.api.kotlin.DescriptorProto
import com.google.api.kotlin.FieldDescriptorProto
import com.google.api.kotlin.FileDescriptorProto
import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.MethodDescriptorProto
import com.google.api.kotlin.ServiceDescriptorProto
import com.google.api.kotlin.TEST_NAMESPACE_KGAX
import com.google.api.kotlin.TestableFunSpec
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.ConfigurationMetadata
import com.google.api.kotlin.config.FlattenedMethod
import com.google.api.kotlin.config.MethodOptions
import com.google.api.kotlin.config.ProtobufTypeMapper
import com.google.api.kotlin.config.ServiceOptions
import com.google.api.kotlin.futureCall
import com.google.api.kotlin.isPublic
import com.google.api.kotlin.messageType
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.DescriptorProtos
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.asTypeName
import kotlin.test.Test
import kotlin.test.assertTrue

// tests basic functionality
// more complex tests use the test protos in [GRPCGeneratorTest].
class FunctionsImplTest {

    @Test
    fun `Generates some fun`() {
        doTest(mock {}, hasNormal = true, hasFlat = false)
    }

    @Test
    fun `Generates some more fun`() {
        val method: MethodOptions = mock {
            on { name } doReturn "wrong"
            on { flattenedMethods } doReturn (listOf(FlattenedMethod(listOf("da_field"))))
        }
        doTest(mock {
            on { methods } doReturn (listOf(method))
        }, hasNormal = true, hasFlat = false)
    }

    @Test
    fun `Generates some flat fun`() {
        val method: MethodOptions = mock {
            on { name } doReturn "FunFunction"
            on { flattenedMethods } doReturn (listOf(FlattenedMethod(listOf("da_field"))))
            on { keepOriginalMethod } doReturn false
        }
        doTest(mock {
            on { methods } doReturn (listOf(method))
        }, hasNormal = false, hasFlat = true)
    }

    // helper
    private fun doTest(opts: ServiceOptions, hasNormal: Boolean, hasFlat: Boolean) {
        assertTrue { hasNormal || hasFlat }

        val unitTestGen: UnitTest = mock {}
        val meta: ConfigurationMetadata = mock {
            on { scopesAsLiteral } doReturn "\"a\", \"b c d e\""
            on { get(any<String>()) } doReturn opts
            on { get(any<DescriptorProtos.ServiceDescriptorProto>()) } doReturn opts
        }
        val types: ProtobufTypeMapper = mock {
            on { getKotlinType(".foo.bar.ZaInput") } doReturn ClassName("foo.bar", "ZaInput")
            on { getKotlinType(".foo.bar.ZaOutput") } doReturn ClassName("foo.bar", "ZaOutput")
            on { getProtoTypeDescriptor(any()) } doReturn (DescriptorProto {
                addField(FieldDescriptorProto {
                    name = "da_field"
                    type = DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING
                })
            })
        }
        val ctx: GeneratorContext = mock {
            on { className } doReturn ClassName("foo.bar", "ZaTest")
            on { metadata } doReturn meta
            on { typeMap } doReturn types
            on { proto } doReturn (
                FileDescriptorProto {
                    name = "my-file"
                })
            on { service } doReturn (
                ServiceDescriptorProto {
                    addMethod(MethodDescriptorProto {
                        name = "FunFunction"
                        inputType = ".foo.bar.ZaInput"
                        outputType = ".foo.bar.ZaOutput"
                    })
                })
        }

        val result = FunctionsImpl(unitTestGen).generate(ctx)
        assertThat(result).hasSize(2)

        validatePrepare(result.first { it.function.name == "prepare" })

        val theFun = result.first { it.function.name == "funFunction" }
        assertThat(theFun.function.returnType).isEqualTo(
            futureCall("ZaOutput", packageName = "foo.bar")
        )
        assertThat(theFun.function.isConstructor).isFalse()
        assertThat(theFun.function.isAccessor).isFalse()
        assertThat(theFun.function.modifiers).isPublic()
        assertThat(theFun.function.receiverType).isNull()
        assertThat(theFun.function.typeVariables).isEmpty()
        assertThat(theFun.function.parameters).hasSize(1)
        if (hasNormal) {
            assertThat(theFun.function.parameters[0].name).isEqualTo("request")
            assertThat(theFun.function.parameters[0].type).isEqualTo(
                messageType("ZaInput", packageName = "foo.bar")
            )
            assertThat(theFun.function.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |it.funFunction(request)
                |}""".asNormalizedString()
            )
        }
        if (hasFlat) {
            assertThat(theFun.function.parameters[0].name).isEqualTo("daField")
            assertThat(theFun.function.parameters[0].type).isEqualTo(String::class.asTypeName())
            assertThat(theFun.function.body.asNormalizedString()).isEqualTo(
                """
                |return stubs.future.executeFuture {
                |it.funFunction(foo.bar.ZaInput.newBuilder()
                |    .setDaField(daField)
                |    .build())
                |}""".asNormalizedString()
            )
        }
    }

    private fun validatePrepare(prepareFun: TestableFunSpec) {
        assertThat(prepareFun.function.returnType).isEqualTo(
            messageType("ZaTest", packageName = "foo.bar")
        )
        assertThat(prepareFun.function.isConstructor).isFalse()
        assertThat(prepareFun.function.isAccessor).isFalse()
        assertThat(prepareFun.function.modifiers).isPublic()
        assertThat(prepareFun.function.receiverType).isNull()
        assertThat(prepareFun.function.typeVariables).isEmpty()
        assertThat(prepareFun.function.parameters).hasSize(1)
        assertThat(prepareFun.function.parameters[0].name).isEqualTo("init")
        assertThat(prepareFun.function.parameters[0].type).isEqualTo(
            LambdaTypeName.get(
                ClassName(
                    "$TEST_NAMESPACE_KGAX.grpc",
                    "ClientCallOptions.Builder"
                ), listOf(), Unit::class.asTypeName()
            )
        )
        assertThat(prepareFun.function.body.asNormalizedString()).isEqualTo(
            """
                |val options = $TEST_NAMESPACE_KGAX.grpc.ClientCallOptions.Builder(options)
                |options.init()
                |return foo.bar.ZaTest(channel, options.build())""".trimIndent().asNormalizedString()
        )
    }
}
