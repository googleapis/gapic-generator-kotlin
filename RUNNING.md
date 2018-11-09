# Usage

## Getting Started

Kgen can be used with docker, gradle, or as a protoc plugin. 

Docker is the simpliest option and works best if you plan to ship the generated code as
part of a library. Gradle is a good choice when you want to use the generated code in a 
project, and protoc is for advanced use cases when you need more control over the process.

*Note* This project is incubating and releases are not yet being published. This document
shows how to use [jitpack](https://jitpack.io/) to get pre-release artifacts that you can
use to try it out in your projects.

### Prerequisites

Write your API using proto3 as described in the [Language Guide](https://developers.google.com/protocol-buffers/docs/proto).

### Docker

To build the executable docker image locally run the following:

```bash
$ cd generator
$ ./gradlew build && docker build . -t kgen
```

Use the generator by mounting your input protocol buffers directory at `/proto` and mounting an 
output directory at `/generated`. For example:

```bash
$ mkdir example-output
$ docker run -it --rm \
         --mount type=bind,source="$(pwd)"/generator/example-server/src/main/proto,target=/proto \
         --mount type=bind,source="$(pwd)"/example-output,target=/generated \
         kgen
```

That's it. Be sure to add the required [dependencies](#dependencies) to your project when using
the generated code.

### Gradle

To use gradle put all of your `.proto` files in `app/src/main/proto` (Android) or `src/main/proto` (non-Android)
and let the [Protobuf plugin for gradle](https://github.com/google/protobuf-gradle-plugin) take care
of the rest.
  
Configure your application's `build.gradle` as shown below:
  
*Note* the following example is for an Android application, but the process is nearly 
identical for a standalone Kotlin application.

```groovy
plugins {
    id "com.google.protobuf" version "0.8.5"
}

// if you are not making an Android app use the vanilla java or kotlin plugin(s)
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'
apply plugin: 'kotlin-android-extensions'

dependencies {
    // add the required runtime (includes gRPC and protobuf)
    implementation 'com.github.googleapis.gax-kotlin:kgax-grpc:master-SNAPSHOT'

    // For android projects, use the android variant instead
    //implementation 'com.github.googleapis.gax-kotlin:kgax-grpc-android:master-SNAPSHOT'
}

// configure the protobuf gradle plugin
protobuf {
    // set the version of protobuf compiler to use
    protoc {
        artifact = 'com.google.protobuf:protoc:3.6.1'
    }
    // set the version of the code generators to use
    plugins {
        // For android projects, uncomment the lines below
        //javalite {
        //    artifact = 'com.google.protobuf:protoc-gen-javalite:3.0.0'
        //}
        client {
            artifact = 'com.github.googleapis:gapic-generator-kotlin:master-SNAPSHOT:core@jar'
        }
    }
    // run the code generators
    generateProtoTasks {
        all().each { task ->
            // For android projects, uncomment the lines below
            //task.builtins {
            //    remove java
            //}
            task.plugins {
                // For android projects, uncomment the lines below
                //javalite {}

                // this generates your client library and helper Kotlin builders!
                client {}
            }
        }
    }
}
```
      
Build your application with gradle as usual:

```bash
        $ ./gradlew build
```

Enjoy your new client library! The generated source code will available on the classpath
for your application to use, and you can find it at `app/build/generated/source/proto`
(Android) or `build/generated/source/proto` (standalone application).

### Protoc

See the [reference documentation](https://developers.google.com/protocol-buffers/docs/reference/java-generated).

## Dependencies

You will need to add [one dependency](https://github.com/googleapis/gax-kotlin) to your build to 
use the generated code. Until releases are published the easiest way to get the artifacts is via
Jitpack:

```groovy
repositories {
   // ...
   maven { url 'https://jitpack.io' }
}

dependencies {
    // pick the ONE dependency that is appropriate for your platform (server or Android) 
    implementation 'com.github.googleapis.gax-kotlin:kgax-grpc:master-SNAPSHOT'
    //implementation 'com.github.googleapis.gax-kotlin:kgax-grpc-android:master-SNAPSHOT'
}
```

## Code Formatters

This project uses dockerized versions of Intellij CE's code formatter,
[Google Java Format](https://github.com/google/google-java-format) and [ktlint](https://ktlint.github.io/). 
They can be customized for the generator and they can be used standalone.

### Building

Create standalone dockerized formatters using the `--target` flag:

```
  $ docker build --target formatter . -t formatter
  $ docker build --target javaformatter . -t javaformatter
  $ docker build --target ktlint . -t ktlint
```

### Usage

Run the container and mount the directory that contains the source files that you want to 
format to `/src` inside the container. It will format all files recursively using the rules defined 
in `format.xml`. For example, to format the files in the current directory use:

```
  $ docker run --rm -it -v $PWD:/src formatter
  $ docker run --rm -it -v $PWD:/src javaformatter
  $ docker run --rm -it -v $PWD:/src ktlint
```

### Customizing

You can replace `/usr/ide/format.xml` with your own formatter configuration to customize
the settings for the intelliJ formatter. See the official [documentation](https://www.jetbrains.com/help/idea/settings-code-style.html)
for more details.

Alternatively, you may override the defaults for any of the formatters by passing arguments to the commands. 
See the `Dockerfile` for the default set of arguments.

### Why So Many Formatters

ktlint does not currently fix long lines so the Intellij formatter is used for most things, and it
gives us options. Alright, perhaps we just like formatters...
