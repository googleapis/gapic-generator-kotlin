# Kgen

Kgen creates Kotlin clients from a [protocol buffer](https://developers.google.com/protocol-buffers/docs/proto3) description of an API. 

[![CircleCI](https://circleci.com/gh/googleapis/gapic-generator-kotlin/tree/master.svg?style=svg)](https://circleci.com/gh/googleapis/gapic-generator-kotlin/tree/master)
[![codecov](https://codecov.io/gh/googleapis/gapic-generator-kotlin/branch/master/graph/badge.svg)](https://codecov.io/gh/googleapis/gapic-generator-kotlin)

*Note* This project is a preview. Please try it out and let us know what you think, but there 
are currently no guarantees of any form of stability or support.

## Quick Start

Kgen can be used with [docker](https://www.docker.com/), [gradle](https://gradle.org/), 
or as a [protoc plugin](https://developers.google.com/protocol-buffers/). 

To get started with docker, clone the project and run the following to generate a client for the [example service](generator/example-server).

```bash
$ mkdir my-output 
$ docker run --rm \
             --mount type=bind,source="$(pwd)"/generator/example-server/src/main/proto,target=/proto \
             --mount type=bind,source="$(pwd)"/my-output,target=/generated \
         gcr.io/kotlin-gapic/kgen
```

See the [RUNNING.md](RUNNING.md) for more details, configuration, and command line options.

## Example

A simple "hello world" style example is in the [generator/example-server](generator/example-server)
and [generator/example-client](generator/example-client) directories. The most relevant files are:

  + The [proto describing the API](generator/example-server/src/main/proto/google/example/hello.proto)
  + The [API implementation](generator/example-server/src/main/kotlin/example/ExampleServer.kt) 
  + The [code calling the API](generator/example-client/src/main/kotlin/example/Client.kt) using the generated client

You can run the client and server locally by running:

```bash
$ cd generator/example-server
$ ./gradlew run
$ cd generator/example-client
$ ./gradlew run
```

More complex examples, using Google Cloud APIs on Android, can be found in the 
[examples directory](examples/README.md).

## Configuration

Kgen can be configured to produce Kotlin code that's easy to use in various flavors. See the
[CONFIGURATION.md](CONFIGURATION.md) to learn about these additional features.

## Why Kgen?

Protocol buffers and gRPC have great tool chains, but they do not have first class support for Kotlin and 
they do not provide many configuration options for generated code. Kgen generates idiomatic Kotlin clients
for protobuf APIs and introduces new configuration options to make the code even more enjoyable to use.

## Contributing

Contributions to this library are always welcome and highly encouraged.

See the [CONTRIBUTING](CONTRIBUTING.md) documentation for more information on how to get started.

## Versioning

This library is currently a *preview* with no guarantees of stability or support. Please get involved and let us know
if you find it useful and we'll work towards a stable version.

## Disclaimer

This is not an official Google product.
