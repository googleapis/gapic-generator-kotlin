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

plugins {
    idea
    application
    kotlin("jvm") version "1.3.30"
    id("com.google.protobuf") version "0.8.17"
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

application {
    mainClassName = "example.Client"
}

defaultTasks = listOf("run")

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    // normally get the KGax library via jitpack:
    //    (this library is in preview and not yet published to maven)
    //
    // repositories {
    //   maven { url 'https://jitpack.io' }
    // }
    // dependencies {
    //   compile("com.github.googleapis.gax-kotlin:kgax-grpc:v0.6.0")
    // }
    //
    // but we use a local copy for development
    //
    // Note: must use compile if referencing the included protos in this archive:
    //   https://github.com/google/protobuf-gradle-plugin/issues/242
    compile("com.google.api:kgax-grpc:0.7.0-SNAPSHOT")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.1.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")

    // needed to unit test with suspend functions (can remove when the dependency above is updated most likely)
    testImplementation("org.mockito:mockito-core:2.23.4")
}

java {
    sourceSets {
        // add the proto file from the server project
        getByName("main").proto.srcDir("../example-server/src/main/proto")
        // add generated unit tests to the project
        getByName("test").java.srcDir("${project.buildDir}/generated/source/clientTest")
    }
}

// compile proto and generate Kotlin clients!
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.7.1"
    }
    plugins {
        id("client") {
            // get the KGen code generator
            // this is normally done through the package manager, i.e.:
            //   artifact = 'com.github.googleapis:gapic-generator-kotlin:master-SNAPSHOT:core@jar'
            // but these examples are used for testing so we'll use a local copy instead
            path = "$projectDir/../runLocalGenerator.sh"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // generate the Kotlin API clients
                id("client") {
                    // this option will add generated unit tests for the client to the project
                    // be sure to add the directory to your test source set(s) as shown above
                    option("test-output=${project.buildDir}/generated/source/clientTest")
                }
            }
        }
    }
}
