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

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    idea
    java
    application
    `maven-publish`
    jacoco
    kotlin("jvm") version "1.3.21"
    id("org.springframework.boot") version "2.1.3.RELEASE"
    id("com.google.protobuf") version "0.8.8"
}

group = "com.google.api"
version = "0.3.0-SNAPSHOT"

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

val ktlintImplementation by configurations.creating
val testSimpleImplementation by configurations.creating {
    extendsFrom(configurations.implementation)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")

    implementation("io.github.microutils:kotlin-logging:1.5.4")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.11.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    implementation("com.squareup:kotlinpoet:1.0.1")

    implementation("org.yaml:snakeyaml:1.20")
    implementation("org.apache.commons:commons-io:1.3.2")
    implementation("org.apache.commons:commons-text:1.4")

    implementation("com.google.guava:guava:25.1-jre")
    implementation("com.google.protobuf:protobuf-java:3.5.1")
    implementation("com.github.pcj:google-options:1.0.0")

    testImpl(kotlin("test"))
    testImpl(kotlin("test-junit"))
    testImpl("junit:junit:4.12")
    testImpl("com.nhaarman:mockito-kotlin:1.6.0")
    // needed to unit test with suspend functions (can remove when the dependency above is updated most likely)
    testImpl("org.mockito:mockito-core:2.23.4")
    testImpl("com.google.truth:truth:0.41")

    // for compiling and running the generated test clients / unit tests
    testImpl("com.google.api:kgax-grpc:0.3.0-SNAPSHOT")

    ktlintImplementation("com.github.shyiko:ktlint:0.30.0")
}

fun DependencyHandlerScope.testImpl(obj: Any) {
    testImplementation(obj)
    testSimpleImplementation(obj)
}

application {
    mainClassName = "com.google.api.kotlin.ClientPluginKt"
}

val testDataDir = "$buildDir/generated/testdata"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    sourceSets {
        create("testSimple") {
            compileClasspath += sourceSets["main"].output
            runtimeClasspath += sourceSets["main"].output
        }
        for (sSet in listOf("main", "test")) {
            getByName(sSet).proto.srcDir("$projectDir/../gax-kotlin/api-common-protos")
        }
        for (sSet in listOf("test", "testSimple")) {
            getByName(sSet).output.dir(mapOf("builtBy" to "updateTestBaselines"), testDataDir)
        }
    }
}

jacoco {
    toolVersion = "0.8.2"
}

publishing {
    publications {
        create<MavenPublication>("bootJava") {
            artifactId = "gapic-generator-kotlin"
            artifact(tasks.getByName("bootJar"))
        }
    }
}

tasks {
    val test = getByName("test")
    val check = getByName("check")
    val clean = getByName("clean")

    val bootJar = withType<BootJar> {
        enabled = true
        baseName = "gapic-generator-kotlin"
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

    val updateTestBaselines by creating {
        doLast {
            val baselinePath = "$testDataDir/baselines"
            delete(baselinePath)

            for (name in listOf("test", "testSimple")) {
                val output = file("$baselinePath/$name.baseline.txt")
                file(output.parent).mkdirs()
                val baseDir = "$buildDir/generated/source/proto/$name/local"

                output.bufferedWriter().use { w ->
                    val sources = file(baseDir).walk()
                        .filter { it.isFile }
                        .sortedBy { it.absolutePath }
                    for (file in sources) {
                        val fileName = file.relativeTo(file(baseDir)).path
                        w.write("-----BEGIN:$fileName-----\n")
                        w.write(file.bufferedReader().use { it.readText() })
                        w.write("-----END:$fileName-----\n")
                        w.flush()
                    }
                }
            }
        }
        outputs.upToDateWhen { false }
    }

    val testSimpleTest by creating(Test::class) {
        testClassesDirs = java.sourceSets["testSimple"].output.classesDirs
        classpath = java.sourceSets["testSimple"].runtimeClasspath
    }
    testSimpleTest.dependsOn(bootJar)
    check.dependsOn(testSimpleTest)
}

// compile proto and generate gRPC stubs
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
    plugins {
        id("gen-test") {
            path = "$projectDir/src/test/resources/plugin-test.sh"
        }
        id("gen-simple") {
            path = "$projectDir/src/test/resources/plugin-simple.sh"
        }
        id("local") {
            path = "$projectDir/../runLocalGenerator.sh"
        }
    }
    generateProtoTasks {
        ofSourceSet("test").forEach {
            it.plugins {
                id("gen-test") {}
                id("local") {
                    option("test-output=$buildDir/generated/source/proto/test/local")
                }
            }
            it.dependsOn("assembleBootDist")
            it.requireBaselineIfNeeded("test")
        }
        ofSourceSet("testSimple").forEach {
            it.plugins {
                id("gen-simple") {}
                id("local") {
                    option("test-output=$buildDir/generated/source/proto/testSimple/local")
                }
            }
            it.dependsOn("assembleBootDist")
            it.requireBaselineIfNeeded("testSimple")
        }
    }
}

fun GenerateProtoTask.requireBaselineIfNeeded(name: String) {
    if (this.name == "generate${name.capitalize()}Proto") {
        tasks.getByName("updateTestBaselines").dependsOn(this)
    }
}
