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
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.ofSourceSet
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    idea
    java
    kotlin("jvm") version "1.3.21"
    id("com.google.protobuf") version "0.8.8"
}

group = "com.google.api"

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
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")

    implementation("com.google.api:kgax-grpc:0.3.0-SNAPSHOT")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("com.google.truth:truth:0.41")

    ktlintImplementation("com.github.shyiko:ktlint:0.30.0")
}

kotlin {
    sourceSets {
        getByName("test").kotlin.srcDir("build/generated/source/proto/test/client")
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
    plugins {
        id("client") {
            path = "$projectDir/../runLocalGenerator.sh"
        }
    }
    generateProtoTasks {
        ofSourceSet("test").forEach {
            it.plugins {
                id("client") {}
            }
        }
    }
}

tasks {
    val check = getByName("check")

    withType<Test> {
        testLogging {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
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
