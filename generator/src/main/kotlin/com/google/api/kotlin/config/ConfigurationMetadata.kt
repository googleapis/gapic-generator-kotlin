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
 * Metadata not in the proto file (i.e. info from the service config).
 *
 * Note: this will need to change significantly when the configuration is moved into the
 * protos, so this implementation is being kept to a minimum in the meantime.
 *
 * @author jbolinger
 */
internal class ConfigurationMetadata constructor(
    val host: String,
    val scopes: List<String>,
    val branding: BrandingOptions,
    private val packageName: String,
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

    /**
     * Get the scopes as a formatted literal string (for use with $L).
     *
     * @return formatted string
     */
    val scopesAsLiteral: String
        get() {
            if (scopes.isEmpty()) return ""
            return '"'.toString() + scopes.joinToString("\",\n\"") + '"'.toString()
        }
}

/** Factory from creating new [ConfigurationMetadata] */
internal class ConfigurationMetadataFactory(val rootDirectory: String = "") {
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
    fun find(proto: DescriptorProtos.FileDescriptorProto): ConfigurationMetadata {
        val protoPath = Paths.get(rootDirectory, proto.name)

        val version = protoPath.parent?.fileName ?: "unknown_version"

        // find all config files
        val matcher = { name: String, pattern: String ->
            FilenameUtils.wildcardMatch(name, pattern, IOCase.INSENSITIVE)
        }
        val serviceConfig = Files.list(protoPath.parent?.parent)
            .map { it.toFile() }
            .filter { it.isFile }
            .filter { matcher(it.name, "*_$version.yaml") }
            .findFirst()
            .orElse(null)
        val clientConfig = Files.list(protoPath.parent)
            .map { it.toFile() }
            .filter { it.isFile }
            .filter { matcher(it.name, "*_gapic.yaml") }
            .findFirst()
            .orElse(null)

        // parse them
        return parse(proto.`package`, serviceConfig, clientConfig)
    }

    // Parses the configuration (service yaml) files.
    private fun parse(
        packageName: String,
        serviceFile: File?,
        clientFile: File?
    ): ConfigurationMetadata {
        val apiConfig = if (clientFile != null) {
            log.debug { "parsing config file: ${clientFile.absolutePath}" }
            parseClient(clientFile)
        } else {
            log.warn { "No service configuration found for package: $packageName (using defaults)" }
            mapOf()
        }

        if (serviceFile != null) {
            FileInputStream(serviceFile).use { ins ->
                val config = yaml.loadAs(ins, ServiceConfigYaml::class.java)

                // parse out the useful info
                val name = config.title
                val summary = config.documentation?.summary
                    ?: "Client library for a wonderful product!"
                val host = config.name
                val scopes = config.authentication?.rules
                    ?.filter { it.oauth != null }
                    ?.map { it.oauth!! }
                    ?.map { it.canonical_scopes }
                    ?.map { it.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() }
                    ?.flatMap { it.asIterable() }
                    ?.map { it.replace("\n".toRegex(), "").trim { it <= ' ' } }
                    ?.toSet() ?: setOf()

                // put it all together
                return ConfigurationMetadata(
                    host, scopes.toList(),
                    BrandingOptions(name, summary),
                    packageName, apiConfig
                )
            }
        } else {
            // use defaults
            log.warn { "No gapic configuration found for package: $packageName (using defaults)" }
            return ConfigurationMetadata(
                "service.example.com", listOf("https://example.com/auth/scope"),
                BrandingOptions("Example API", "No configuration was provided for this API!"),
                packageName, apiConfig
            )
        }
    }

    // parses the config (gapic yaml) files
    private fun parseClient(file: File): Map<String, ServiceOptions> {
        FileInputStream(file).use { ins ->
            val config = yaml.loadAs(ins, GapicYaml::class.java)

            // parse method configuration
            val services = mutableMapOf<String, ServiceOptions>()
            config.interfaces.forEach { service ->
                services[service.name] = ServiceOptions(service.methods.map { method ->
                    val flattening =
                        method.flattening?.groups?.map { FlattenedMethod(it.parameters.map { it.asPropertyPath() }) }
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
                })
            }
            return services.toMap()
        }
    }
}

/** Branding options for product [name], [url], etc. */
internal data class BrandingOptions(
    val name: String,
    val summary: String,
    val url: String = "http://www.google.com"
)

/** Code generator options for a set of APIs methods within a protobuf service */
internal data class ServiceOptions(val methods: List<MethodOptions> = listOf())

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
