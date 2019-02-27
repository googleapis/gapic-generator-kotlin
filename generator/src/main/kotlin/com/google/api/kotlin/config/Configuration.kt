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
import com.google.api.kotlin.ClientPluginOptions
import com.google.api.kotlin.ConfigurationFactory
import com.google.api.kotlin.util.isIntOrLong
import com.google.api.kotlin.util.isRepeated
import com.google.api.kotlin.util.isString
import com.google.longrunning.OperationsProto
import com.google.protobuf.DescriptorProtos
import com.google.rpc.Code
import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOCase
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

/**
 * Configuration metadata for customizing the API surface for a set of RPC services.
 */
internal class Configuration constructor(
    val packageName: String,
    val authentication: AuthOptions,
    val branding: BrandingOptions,
    private val serviceOptions: Map<String, ServiceOptions>
) {

    /** Get the options for the given service */
    operator fun get(serviceName: String): ServiceOptions {
        val opt = serviceOptions[serviceName]
        if (opt == null) {
            log.warn { "No service defined with name: $serviceName (using default options)" }
        }
        return opt ?: ServiceOptions()
    }

    /** Get the options for the given service */
    operator fun get(service: DescriptorProtos.ServiceDescriptorProto) =
        get("$packageName.${service.name}")
}

private const val PAGE_FIELD_RESPONSES = "responses"
private const val PAGE_FIELD_SIZE = "page_size"
private const val PAGE_FIELD_TOKEN = "page_token"
private const val PAGE_FIELD_NEXT_TOKEN = "next_page_token"

/** Factory for creating a new [Configuration]. */
internal class AnnotationConfigurationFactory(
    private val authOptions: AuthOptions,
    private val typeMap: ProtobufTypeMapper
) : ConfigurationFactory {

    override fun fromProto(proto: DescriptorProtos.FileDescriptorProto): Configuration {
        val metadata = proto.options.getExtensionOrNull(ClientProto.clientPackage)

        // parse package name
        val packageName = if (metadata != null && metadata.namespaceCount > 0) {
            metadata.namespaceList.joinToString(".")
        } else {
            proto.`package`!!
        }.toLowerCase().replace("\\s".toRegex(), "")

        // parse branding (non-code) items
        val branding = BrandingOptions(
            name = metadata?.productTitle ?: metadata?.title ?: "",
            url = "[TODO: URL was removed from the spec]"
        )

        // parse API method config for each service
        val apiConfig = proto.serviceList.associate {
            "${proto.`package`}.${it.name}" to getOptionsForService(proto, it)
        }

        // put it all together
        return Configuration(
            packageName = packageName,
            authentication = authOptions,
            branding = branding,
            serviceOptions = apiConfig
        )
    }

    // parse service level metadata
    private fun getOptionsForService(
        proto: DescriptorProtos.FileDescriptorProto,
        service: DescriptorProtos.ServiceDescriptorProto
    ): ServiceOptions {
        var host = service.options.getExtensionOrNull(ClientProto.defaultHost) ?: ""
        val scopes = service.options.getExtensionOrNull(ClientProto.oauthScopes)?.split(",") ?: listOf()
        val methods = service.methodList.map { getOptionsForServiceMethod(proto, it) }

        // don't allow blank host & default to localhost
        if (host.isBlank()) {
            host = "localhost"
        }

        val options = ServiceOptions(
            host = host,
            scopes = scopes,
            methods = methods
        )

        log.debug { "Using the following settings for service: ${service.name}:\n$options" }

        return options
    }

    // parse method level metadata
    private fun getOptionsForServiceMethod(
        proto: DescriptorProtos.FileDescriptorProto,
        method: DescriptorProtos.MethodDescriptorProto
    ): MethodOptions {
        val signatures = method.options.getExtensionOrNull(ClientProto.methodSignature) ?: listOf()
        val httpBindings = method.options.getExtensionOrNull(AnnotationsProto.http)

        // TODO: retry was removed from the spec - will it return?
        // val retry = method.options.getExtensionOrNull(AnnotationsProto.retry)
        // var retryOptions = if (retry != null) ClientRetry(retry.codesList) else null

        var retryOptions: ClientRetry? = null

        // TODO: headers?
        if (httpBindings != null) {
            log.warn { "Regional headers are not implemented!" }

            // use a default try if not specified
            if (retryOptions == null && httpBindings.get.isNotBlank()) {
                retryOptions = ClientRetry(listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED))
            }
        }

        // TODO: samples?

        val pagedResponse = getMethodPagedResponse(method)
        return MethodOptions(
            name = method.name,
            flattenedMethods = signatures.map { getMethodFrom(it, pagedResponse) },
            keepOriginalMethod = true,
            pagedResponse = pagedResponse,
            longRunningResponse = getLongRunningResponse(proto, method),
            retry = retryOptions,
            samples = listOf()
        )
    }

    // parse method signatures
    private fun getMethodFrom(signature: String, paging: PagedResponse?): FlattenedMethod {
        // add all paths
        val paths = signature.split(",")
            .map { it.asPropertyPath() }
            .toMutableList()

        // when paging always embed the page size at the end
        if (paging != null) {
            paths.add(paging.pageSize.asPropertyPath())
        }

        // add this method and any nested signature
        return FlattenedMethod(paths)
    }

    // determine if a method can be paged
    private fun getMethodPagedResponse(
        method: DescriptorProtos.MethodDescriptorProto
    ): PagedResponse? {
        // look for the required input fields and bail out if they are missing
        val outputType = typeMap.getProtoTypeDescriptor(method.outputType)
        val responsesField = outputType.fieldList.find { it.name == PAGE_FIELD_RESPONSES } ?: return null
        val nextPageTokenField = outputType.fieldList.find { it.name == PAGE_FIELD_NEXT_TOKEN } ?: return null

        // do the same for the output fields
        val inputType = typeMap.getProtoTypeDescriptor(method.inputType)
        val pageSizeField = inputType.fieldList.find { it.name == PAGE_FIELD_SIZE } ?: return null
        val pageTokenField = inputType.fieldList.find { it.name == PAGE_FIELD_TOKEN } ?: return null

        // verify the types
        if (!responsesField.isRepeated()) return null
        if (!nextPageTokenField.isString() || nextPageTokenField.isRepeated()) return null
        if (!pageTokenField.isString() || pageTokenField.isRepeated()) return null
        if (!pageSizeField.isIntOrLong() || pageTokenField.isRepeated()) return null

        return PagedResponse(
            pageSize = pageSizeField.name,
            requestPageToken = pageTokenField.name,
            responsePageToken = nextPageTokenField.name,
            responseList = responsesField.name
        )
    }

    // determine if a method uses a long running response type
    private fun getLongRunningResponse(
        proto: DescriptorProtos.FileDescriptorProto,
        method: DescriptorProtos.MethodDescriptorProto
    ): LongRunningResponse? {
        val longRunning = method.options.getExtensionOrNull(OperationsProto.operationInfo)

        if (longRunning != null) {
            // extract response and metadata type
            var responseType = longRunning.responseType
            if (!responseType.contains(".")) {
                responseType = ".${proto.`package`}.$responseType"
            }
            var metadataType = longRunning.metadataType
            if (!metadataType.contains(".")) {
                metadataType = ".${proto.`package`}.$metadataType"
            }

            // ensure classes are fully qualified
            if (!responseType.startsWith(".")) {
                responseType = ".$responseType"
            }
            if (!metadataType.startsWith(".")) {
                metadataType = ".$metadataType"
            }

            return LongRunningResponse(responseType, metadataType)
        }

        return null
    }
}

/**
 * Factory for creating a new [Configuration] by delegating to the annotation or legacy
 * configuration factory (uses annotation if the product_name or product_uri annotation
 * is found.
 *
 * This will be removed before any stable release.
 */
internal class SwappableConfigurationFactory(
    rootDirectory: String = "",
    authentication: AuthOptions,
    typeMap: ProtobufTypeMapper
) : ConfigurationFactory {
    private val annotation = AnnotationConfigurationFactory(authentication, typeMap)
    private val legacy = LegacyConfigurationFactory(authentication, rootDirectory)

    override fun fromProto(proto: DescriptorProtos.FileDescriptorProto) =
        getFactory(proto).fromProto(proto)

    private fun getFactory(proto: DescriptorProtos.FileDescriptorProto): ConfigurationFactory {
        val hasAnnotation = proto.serviceList.any { it.options.getExtensionOrNull(ClientProto.defaultHost) != null } ||
            proto.options.getExtensionOrNull(ClientProto.clientPackage)?.title?.length ?: 0 > 0

        return if (hasAnnotation) {
            log.debug { "Using annotation based config for ${proto.name}" }
            annotation
        } else {
            log.debug { "Using yaml based config for ${proto.name}" }
            legacy
        }
    }
}

/** Create a config factory from a set of [ClientPluginOptions]. */
internal fun ClientPluginOptions.asSwappableConfiguration(typeMap: ProtobufTypeMapper): SwappableConfigurationFactory {
    val auth = mutableListOf<AuthTypes>()
    if (this.authGoogleCloud) {
        auth += AuthTypes.GOOGLE_CLOUD
    }
    return SwappableConfigurationFactory(this.sourceDirectory, AuthOptions(auth), typeMap)
}

/**
 * Factory for creating a new [Configuration].
 *
 * This uses the legacy configuration yaml file and will be removed before any stable release.
 */
internal class LegacyConfigurationFactory(
    val authentication: AuthOptions,
    private val rootDirectory: String = ""
) : ConfigurationFactory {
    private val yaml: Yaml
        get() {
            val representer = Representer()
            representer.propertyUtils.isSkipMissingProperties = true
            return Yaml(Constructor(ServiceConfigYaml::class.java), representer)
        }

    /**
     * Attempts to find the file given the proto assuming the directory
     * structure conforms to:
     *
     *    /api/v1/my_service.proto
     *    /api/v1/my_gapic.yaml
     *    /api/my_v1.yaml
     */
    override fun fromProto(proto: DescriptorProtos.FileDescriptorProto): Configuration {
        val protoPath = Paths.get(rootDirectory, proto.name)

        val version = protoPath.parent?.fileName ?: "unknown_version"

        // find all config files
        val matcher = { name: String, pattern: String ->
            FilenameUtils.wildcardMatch(name, pattern, IOCase.INSENSITIVE)
        }

        // the service config
        val serviceConfig = try {
            Files.list(protoPath.parent?.parent)
                .map { it.toFile() }
                .filter { it.isFile }
                .filter { matcher(it.name, "*_$version.yaml") }
                .findFirst()
                .orElseThrow { IllegalStateException("no candidate file found") }
        } catch (e: Exception) {
            log.warn(e) { "Unable to find service config" }
            null
        }

        // the gapic config
        val clientConfig = try {
            Files.list(protoPath.parent)
                .map { it.toFile() }
                .filter { it.isFile }
                .filter { matcher(it.name, "*_gapic.yaml") }
                .findFirst()
                .orElseThrow { IllegalStateException("no candidate file found") }
        } catch (e: Exception) {
            log.warn(e) { "Unable to find _gapic.yaml" }
            null
        }

        // parse them
        return parse(proto.`package`, serviceConfig, clientConfig)
    }

    // Parses the configuration (service yaml) files.
    private fun parse(
        packageName: String,
        serviceFile: File?,
        clientFile: File?
    ): Configuration {
        // defaults
        var name = ""
        var summary = ""
        var host = ""
        var scopes = setOf<String>()

        // try and parse service config
        if (serviceFile != null) {
            FileInputStream(serviceFile).use { ins ->
                val config = yaml.loadAs(ins, ServiceConfigYaml::class.java)

                // parse out the useful info
                name = config.title
                summary = config.documentation?.summary
                    ?: "Client library for a wonderful product!"
                host = config.name
                scopes = config.authentication?.rules
                    ?.filter { it.oauth != null }
                    ?.map { it.oauth!! }
                    ?.map { it.canonical_scopes }
                    ?.map {
                        it.split(",".toRegex()).dropLastWhile { str -> str.isEmpty() }
                            .toTypedArray()
                    }
                    ?.flatMap { it.asIterable() }
                    ?.map { it.replace("\n".toRegex(), "").trim { c -> c <= ' ' } }
                    ?.toSet() ?: setOf()
            }
        }

        // parse API config options
        val apiConfig = if (clientFile != null) {
            log.debug { "parsing config file: ${clientFile.absolutePath}" }
            parseClient(clientFile, host, scopes)
        } else {
            log.warn { "No service configuration found for package: $packageName (using defaults)" }
            mapOf()
        }

        // put it all together
        return Configuration(
            packageName = packageName,
            authentication = authentication,
            branding = BrandingOptions(name, summary),
            serviceOptions = apiConfig
        )
    }

    // parses the config (gapic yaml) files
    private fun parseClient(
        file: File,
        host: String,
        scopes: Set<String>
    ): Map<String, ServiceOptions> {
        FileInputStream(file).use { ins ->
            val config = yaml.loadAs(ins, GapicYaml::class.java)

            // parse method configuration
            val services = mutableMapOf<String, ServiceOptions>()
            config.interfaces.forEach { service ->
                val methods = service.methods.map { method ->
                    // parse paging setup
                    val paging = method.page_streaming?.let {
                        PagedResponse(
                            it.request.page_size_field, it.request.token_field,
                            it.response.token_field, it.response.resources_field
                        )
                    }

                    // parse flattening setup
                    val flattening =
                        method.flattening?.groups?.map {
                            val paths = it.parameters.map { p -> p.asPropertyPath() }.toMutableList()
                            if (paging != null) {
                                paths.add(paging.pageSize.asPropertyPath())
                            }
                            FlattenedMethod(paths)
                        } ?: listOf()

                    // collect samples
                    val samples = mutableListOf<SampleMethod>()
                    val sample = method.sample_code_init_fields
                        .asSequence()
                        .map { it.split("=".toRegex(), 2) }
                        .filter { it.size == 2 }
                        .map { SampleParameterAndValue(it[0], it[1]) }
                        .toList()
                    if (sample.isNotEmpty()) {
                        samples.add(SampleMethod(sample))
                    }

                    MethodOptions(
                        name = method.name,
                        flattenedMethods = flattening,
                        keepOriginalMethod = method.request_object_method,
                        pagedResponse = paging,
                        longRunningResponse = null,
                        samples = samples,
                        retry = null
                    )
                }
                services[service.name] = ServiceOptions(host, scopes.toList(), methods)
            }
            return services.toMap()
        }
    }
}

/** Branding options for product [name], [url], etc. */
internal data class BrandingOptions(
    val name: String = "",
    val summary: String = "",
    val url: String = "http://www.google.com"
)

/** Authentication options */
internal data class AuthOptions(
    val types: List<AuthTypes> = listOf()
) {
    val hasGoogleCloud by lazy {
        types.contains(AuthTypes.GOOGLE_CLOUD)
    }
}

/** Well known authentication types */
internal enum class AuthTypes {
    GOOGLE_CLOUD
}

/** Code generator options for a set of APIs methods within a protobuf service */
internal data class ServiceOptions(
    val host: String = "",
    val scopes: List<String> = listOf(),
    val methods: List<MethodOptions> = listOf()
)

/** Code generation options for an API method */
internal data class MethodOptions(
    val name: String,
    val flattenedMethods: List<FlattenedMethod> = listOf(),
    val keepOriginalMethod: Boolean = true,
    val pagedResponse: PagedResponse? = null,
    val longRunningResponse: LongRunningResponse? = null,
    val retry: ClientRetry? = null,
    val samples: List<SampleMethod> = listOf()
)

internal data class ClientRetry(val codes: List<Code>)

/** Flattened method with a list of, potentially nested, [parameters] */
internal data class FlattenedMethod(val parameters: List<PropertyPath>)

/** Paged responses */
internal data class PagedResponse(
    val pageSize: String,
    val requestPageToken: String,
    val responsePageToken: String,
    val responseList: String
)

/** Long running responses */
internal data class LongRunningResponse(val responseType: String, val metadataType: String)

/** Sample code (for method docs) */
internal data class SampleMethod(val parameters: List<SampleParameterAndValue>)

/** Value for parameter in the same (string form) */
internal data class SampleParameterAndValue(
    val parameterPath: String,
    val value: String
)

// --------------------------------------------------------------
// Type Mappings (for all the yaml files)
// --------------------------------------------------------------

// TODO: this could be replaced by using the proto definitions directly

internal data class ServiceConfigYaml(
    var type: String = "",
    var name: String = "",
    var title: String = "",
    var apis: List<ServiceConfigApisYaml> = listOf(),
    var documentation: ServiceConfigDocYaml? = null,
    var authentication: ServiceConfigAuthYaml? = null
)

internal data class ServiceConfigApisYaml(var name: String = "")

internal data class ServiceConfigDocYaml(var summary: String = "")

internal data class ServiceConfigAuthYaml(var rules: List<ServiceConfigAuthRuleYaml> = listOf())

internal data class ServiceConfigAuthRuleYaml(
    var selector: String = "",
    var oauth: ServiceConfigOAuthYaml? = null
)

internal data class ServiceConfigOAuthYaml(var canonical_scopes: String = "")

internal data class GapicYaml(
    var type: String = "",
    var interfaces: List<GapicInterfacesYaml> = listOf()
)

internal data class GapicInterfacesYaml(
    var name: String = "",
    var methods: List<GapicMethodsYaml> = listOf()
)

internal data class GapicMethodsYaml(
    var name: String = "",
    var flattening: GapicMethodFlatteningYaml? = null,
    var page_streaming: GapicPageStreamingYaml? = null,
    var sample_code_init_fields: List<String> = listOf(),
    var request_object_method: Boolean = true
)

internal data class GapicMethodFlatteningYaml(var groups: List<GapicMethodFlatteningGroupYaml> = listOf())

internal data class GapicMethodFlatteningGroupYaml(var parameters: List<String> = listOf())

internal data class GapicPageStreamingYaml(
    var request: GapicPageStreamingRequestYaml = GapicPageStreamingRequestYaml(),
    var response: GapicPageStreamingResponseYaml = GapicPageStreamingResponseYaml()
)

internal data class GapicPageStreamingRequestYaml(
    var page_size_field: String = "",
    var token_field: String = ""
)

internal data class GapicPageStreamingResponseYaml(
    var token_field: String = "",
    var resources_field: String = ""
)
