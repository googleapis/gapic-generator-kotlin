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

import com.google.api.kotlin.GeneratorContext
import com.google.api.kotlin.asNormalizedString
import com.google.api.kotlin.config.ConfigurationMetadata
import com.google.api.kotlin.types.GrpcTypes
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import java.io.InputStream
import kotlin.test.Test

private const val namespace = "foo.companion.obj"
private const val classname = "CompClazz"
private const val scopes = "\"sc_1\", \"sc_4\", \"sc\""

class CompanionObjectImplTest {

    @Test
    fun `Generates default scopes property`() {
        val meta: ConfigurationMetadata = mock {
            on { scopesAsLiteral } doReturn "\"a\", \"b c d e\""
        }
        val ctx: GeneratorContext = mock {
            on { className } doReturn ClassName("foo.bar", "ScopesTest")
            on { metadata } doReturn meta
        }
        val type = CompanionObjectImpl().generate(ctx)

        assertThat(type.propertySpecs).hasSize(1)

        val prop = type.propertySpecs.first()
        assertThat(prop.name).isEqualTo("ALL_SCOPES")
        assertThat(prop.initializer.asNormalizedString()).isEqualTo(
            """
                |listOf("a", "b c d e")
            """.asNormalizedString()
        )
    }

    @Test
    fun `Generates factory with access token`() {
        testAuthFactoryMethod(
            "fromAccessToken", arrayOf(
                Pair("accessToken", GrpcTypes.Auth.AccessToken),
                Pair("scopes", List::class.parameterizedBy(String::class)),
                Pair("channel", GrpcTypes.ManagedChannel.asNullable())
            ),
            CodeBlock.of(
                """
                    |val credentials = %T.create(accessToken).createScoped(scopes)
                    |return $namespace.$classname(channel ?: createChannel(), %T(%T.from(credentials)))
                """.trimMargin(),
                GrpcTypes.Auth.GoogleCredentials,
                GrpcTypes.Support.ClientCallOptions,
                GrpcTypes.Auth.MoreCallCredentials
            )
        )
    }

    @Test
    fun `Generates factory with credentials`() {
        testAuthFactoryMethod(
            "fromCredentials", arrayOf(
                Pair("credentials", GrpcTypes.Auth.GoogleCredentials),
                Pair("channel", GrpcTypes.ManagedChannel.asNullable())
            ),
            CodeBlock.of(
                "return $namespace.$classname(channel ?: createChannel(), %T(%T.from(credentials)))",
                GrpcTypes.Support.ClientCallOptions,
                GrpcTypes.Auth.MoreCallCredentials
            )
        )
    }

    @Test
    fun `Generates factory with serviceAccount`() {
        testAuthFactoryMethod(
            "fromServiceAccount", arrayOf(
                Pair("keyFile", InputStream::class.asTypeName()),
                Pair("scopes", List::class.parameterizedBy(String::class)),
                Pair("channel", GrpcTypes.ManagedChannel.asNullable())
            ),
            CodeBlock.of(
                """
                    |val credentials = %T.fromStream(keyFile).createScoped(scopes)
                    |return $namespace.$classname(channel ?: createChannel(), %T(%T.from(credentials)))
                """.trimMargin(),
                GrpcTypes.Auth.GoogleCredentials,
                GrpcTypes.Support.ClientCallOptions,
                GrpcTypes.Auth.MoreCallCredentials)
        )
    }

    @Test
    fun `Generates factory with stubs`() {
        testAuthFactoryMethod(
            "fromStubs", arrayOf(
                Pair("factory", ClassName("", Stubs.CLASS_STUBS, "Factory")),
                Pair("channel", GrpcTypes.ManagedChannel.asNullable()),
                Pair("options", GrpcTypes.Support.ClientCallOptions.asNullable())
            ),
            CodeBlock.of(
                "return $namespace.$classname(channel ?: createChannel(), options ?: %T(), factory)",
                GrpcTypes.Support.ClientCallOptions
            )
        )
    }

    private fun testAuthFactoryMethod(
        funName: String,
        params: Array<Pair<String, TypeName>>,
        body: CodeBlock
    ) {
        val meta: ConfigurationMetadata = mock {
            on { scopesAsLiteral } doReturn scopes
        }
        val ctx: GeneratorContext = mock {
            on { className } doReturn ClassName(namespace, classname)
            on { metadata } doReturn meta
        }

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == funName }
        assertThat(method.kdoc.toString()).isNotEmpty()
        assertThat(method.annotations.map { it.type }).containsExactlyElementsIn(
            arrayOf(JvmStatic::class, JvmOverloads::class).map { it.asClassName() }
        )
        assertThat(method.returnType).isEqualTo(ClassName(namespace, classname))
        assertThat(method.parameters.map { it.name }).containsExactlyElementsIn(
            params.map { it.first }
        ).inOrder()
        assertThat(method.parameters.map { it.type }).containsExactlyElementsIn(
            params.map { it.second }
        ).inOrder()
        assertThat(method.body.asNormalizedString()).isEqualTo(body.asNormalizedString())
    }

    @Test
    fun `Generates create channel method`() {
        val meta: ConfigurationMetadata = mock {
            on { scopesAsLiteral } doReturn scopes
        }
        val ctx: GeneratorContext = mock {
            on { className } doReturn ClassName(namespace, classname)
            on { metadata } doReturn meta
        }

        val type = CompanionObjectImpl().generate(ctx)

        val method = type.funSpecs.first { it.name == "createChannel" }
        assertThat(method.kdoc.toString()).isNotEmpty()
        assertThat(method.annotations.map { it.type }).containsExactlyElementsIn(
            arrayOf(JvmStatic::class, JvmOverloads::class).map { it.asClassName() }
        )
        assertThat(method.returnType).isEqualTo(GrpcTypes.ManagedChannel)
        assertThat(method.parameters.map { it.name }).containsExactly(
            "host", "port", "enableRetry"
        ).inOrder()
        assertThat(method.parameters.map { it.type }).containsExactlyElementsIn(
            arrayOf(String::class, Int::class, Boolean::class).map { it.asClassName() }
        ).inOrder()
        assertThat(method.body.asNormalizedString()).isEqualTo(
            CodeBlock.of(
                """
                    |val builder = %T.forAddress(host, port)
                    |if (enableRetry) {
                    |    builder.enableRetry()
                    |}
                    |return builder.build()
                    |""".trimMargin(),
                GrpcTypes.OkHttpChannelBuilder)
                .asNormalizedString()
        )
    }
}