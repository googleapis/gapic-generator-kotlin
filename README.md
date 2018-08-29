# Kgen

Kgen creates Kotlin API client code from a [protocol buffer](https://developers.google.com/protocol-buffers/docs/proto3) description of the API. 

The clients are currently compatible with gRPC APIs, but we plan to support alternative transports as well.

*Note* This project is a preview. Please try it out and let us know what you think, but there 
are currently no guarantees for any form of stability or support.

## Quick Start

Kgen can be used with [docker](https://www.docker.com/), [gradle](https://gradle.org/), 
or as a [protoc plugin](https://developers.google.com/protocol-buffers/). 

To get started with docker, run the following to generate a client for the [example service](generator/example-server).

```bash
$ mkdir my-output 
$ docker run --rm \
             --mount type=bind,source="$(pwd)"/generator/example-server/src/main/proto,target=/proto \
             --mount type=bind,source="$(pwd)"/my-output,target=/generated \
         gcr.io/kotlin-gapic/kgen
```

Read the next section to learn more about the output, or replace the input directory with 
your own protos to see what happens.

See the [RUNNING.md](RUNNING.md) for more details, configuration, and command line options.

## Examples

A simple "hello world" style example is in the [generator/example-server](generator/example-server)
and Kgen's output, using the command in the quick start, is checked into [generator/example-client](generator/example-client). The most relevant files are:

  + The [proto describing the API](generator/example-server/src/main/proto/google/example/hello.proto)
  + The [API implementation](generator/example-server/src/main/kotlin/example/ExampleServer.kt) 
  + The generated [client code](generator/example-client), which includes:
    + `client`: A client for each of the API services and various `KotlinBuilders.kt` that may be used as an alternative to the Java builders.
    + `clientTest`: Test code for the clients.
    + `grpc`: gRPC stubs (used internally by the clients).
    + `javalite`: Java message types defined in the API.

Kotlin projects come in all shapes and sizes so Kgen does not organize the artifacts into a new 
Kotin project for you. Copy the artifacts that you need into your projects or use the gradle runner.

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
