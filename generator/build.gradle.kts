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

import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    idea
    java
    application
    `maven-publish`
    jacoco
    kotlin("jvm") version "1.3.11"
    id("org.springframework.boot") version "2.1.1.RELEASE"
    id("com.google.protobuf") version "0.8.7"
}

buildscript {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
}

repositories {
    google()
    mavenCentral()
    jcenter()
}

val ktlintImplementation by configurations.creating

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")

    implementation("io.github.microutils:kotlin-logging:1.5.4")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.11.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    implementation("com.squareup:kotlinpoet:1.0.0")

    implementation("org.yaml:snakeyaml:1.20")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("org.apache.commons:commons-text:1.4")

    implementation("com.google.guava:guava:25.1-jre")
    implementation("com.google.protobuf:protobuf-java:3.5.1")
    implementation("com.github.pcj:google-options:1.0.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("com.google.truth:truth:0.41")

    ktlintImplementation("com.github.shyiko:ktlint:0.29.0")
}

base {
    group = "com.google.api"
    version = "0.1.0-SNAPSHOT"
}

application {
    mainClassName = "com.google.api.kotlin.ClientPluginKt"
}

java {
    sourceSets {
        getByName("main") {
            withGroovyBuilder {
                "proto" {
                    "srcDir"("api-common-protos")
                }
            }
        }
    }
}

// compile proto and generate gRPC stubs
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
}

publishing {
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
}

jacoco {
    toolVersion = "0.8.2"
}

tasks {
    val test = getByName("test")
    val check = getByName("check")

    withType<BootJar> {
        enabled = true
        classifier = "core"
        mainClassName = "com.google.api.kotlin.ClientPluginKt"
        launchScript()
    }

    withType<Test> {
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }

    withType<JacocoReport> {
        reports {
            xml.isEnabled = true
            html.isEnabled = true
        }
        sourceDirectories = files(listOf("src/main/kotlin"))
        test.finalizedBy(this)
    }

    val ktlint by creating(JavaExec::class) {
        group = "verification"
        description = "Check Kotlin code style."
        main = "com.github.shyiko.ktlint.Main"
        classpath = ktlintImplementation
        args = listOf("src/**/*.kt", "test/**/*.kt")
    }
    check.dependsOn(ktlint)

    val ktlintFormat by creating(JavaExec::class) {
        group = "formatting"
        description = "Fix Kotlin code style deviations."
        main = "com.github.shyiko.ktlint.Main"
        classpath = ktlintImplementation
        args = listOf("-F", "src/**/*.kt", "test/**/*.kt")
    }
}
