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
import com.google.api.Metadata
import com.google.api.kotlin.ConfigurationFactory
import com.google.protobuf.DescriptorProtos
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
 *
 * @author jbolinger
 */
internal class Configuration constructor(
    val packageName: String,
    val branding: BrandingOptions,
    private val serviceOptions: Map<String, ServiceOptions>
) {

    /** Get the options for the given service */
    operator fun get(serviceName: String): ServiceOptions {
        val opt = serviceOptions[serviceName]
        if (opt == null) {
            log.warn { "No service defined with name: $serviceName (using default optioons)" }
        }
        return opt ?: ServiceOptions()
    }

    operator fun get(service: DescriptorProtos.ServiceDescriptorProto) =
        get("$packageName.${service.name}")
}

/** Factory for creating a new [Configuration]. */
internal class AnnotationConfigurationFactory : ConfigurationFactory {

    override fun fromProto(proto: DescriptorProtos.FileDescriptorProto): Configuration {
        val metadata = parseMetadata(proto)

        val packageName = proto.`package`
        val branding = BrandingOptions(
            name = metadata?.productName ?: "",
            url = metadata?.productUri ?: ""
        )
        val apiConfig = getOptionsForServices(proto)

        return Configuration(packageName, branding, apiConfig)
    }

    // parse API level metadata
    private fun parseMetadata(proto: DescriptorProtos.FileDescriptorProto): Metadata? {
        try {
            val field = proto.options.unknownFields.getField(AnnotationsProto.METADATA_FIELD_NUMBER)
            return Metadata.parseFrom(field.lengthDelimitedList.first())
        } catch (e: Exception) {
            log.warn(e) { "Unable to parse metadata from annotation (not present?)" }
        }
        return null
    }

    private fun getOptionsForServices(proto: DescriptorProtos.FileDescriptorProto): Map<String, ServiceOptions> {
        return mapOf()
    }
}

/**
 * Factory for creating a new [Configuration].
 *
 * This use the legacy configuration yaml file and must be removed before any stable release.
 */
internal class LegacyConfigurationFactory(
    private val rootDirectory: String = ""
) : ConfigurationFactory {
    private val yaml: Yaml
        get() {
            val representer = Representer()
            representer.getPropertyUtils().setSkipMissingProperties(true)
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
            packageName,
            BrandingOptions(name, summary),
            apiConfig)
    }

    // parses the config (gapic yaml) files
    private fun parseClient(file: File, host: String, scopes: Set<String>): Map<String, ServiceOptions> {
        FileInputStream(file).use { ins ->
            val config = yaml.loadAs(ins, GapicYaml::class.java)

            // parse method configuration
            val services = mutableMapOf<String, ServiceOptions>()
            config.interfaces.forEach { service ->
                val methods = service.methods.map { method ->
                    val flattening =
                        method.flattening?.groups?.map { FlattenedMethod(it.parameters.map { p -> p.asPropertyPath() }) }
                            ?: listOf()

                    // collect samples
                    val samples = mutableListOf<SampleMethod>()
                    val sample = method.sample_code_init_fields
                        .map { it.split("=".toRegex(), 2) }
                        .filter { it.size == 2 }
                        .map { SampleParameterAndValue(it[0], it[1]) }
                    if (sample.isNotEmpty()) {
                        samples.add(SampleMethod(sample))
                    }

                    // parse paging setup
                    val paging = method.page_streaming?.let {
                        PagedResponse(
                            it.request.page_size_field, it.request.token_field,
                            it.response.token_field, it.response.resources_field
                        )
                    }

                    MethodOptions(
                        method.name,
                        flattening,
                        method.request_object_method,
                        paging,
                        samples
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
    val samples: List<SampleMethod> = listOf()
)

/** Flattened method with a list of, potentially nested, [parameters] */
internal data class FlattenedMethod(val parameters: List<PropertyPath>)

/** Paged responses */
internal data class PagedResponse(
    val pageSize: String,
    val requestPageToken: String,
    val responsePageToken: String,
    val responseList: String
)

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
