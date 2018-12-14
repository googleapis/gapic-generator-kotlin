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
    kotlin("jvm") version "1.3.11"
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
    maven(url = "https://jitpack.io")
}

application {
    mainClassName = "example.Main"
}

defaultTasks = listOf("run")

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    // get the KGax library via jitpack
    // (this library is in preview and not yet published to maven)
    implementation("com.github.googleapis.gax-kotlin:kgax-grpc:0a3362f")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.11")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    
    // needed to unit test with suspend functions (can remove when the dependency above is updated most likely)
    testImplementation("org.mockito:mockito-core:2.23.4")
}

java {
    sourceSets {
        // add the proto file from the server project
        getByName("main").proto.srcDir("../example-apis")
        // add generated unit tests to the project
        getByName("test").java.srcDir("${project.buildDir}/generated/source/clientTest")
    }
}

// compile proto and generate Kotlin clients!
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.6.1"
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
                id("client") {
                    // this option will add generated unit tests for the client to the project
                    // be sure to add the directory to your test source set(s) as shown above
                    option("test-output=${project.buildDir}/generated/source/clientTest")

                    // this option is not typical. It is used here because the cloud API
                    // definitions used in this example use a legacy configuration format.
                    // This will be removed once they are updated.
                    option("source=$projectDir/../example-apis")

                    // this options should be added for Google APIs
                    option("auth-google-cloud")
                }
            }
        }
    }
}
