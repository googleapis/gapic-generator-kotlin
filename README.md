# Kgen

Kgen creates Kotlin clients from a [protocol buffer](https://developers.google.com/protocol-buffers/docs/proto3) description of an API. 

[![CircleCI](https://circleci.com/gh/googleapis/gapic-generator-kotlin/tree/master.svg?style=svg)](https://circleci.com/gh/googleapis/gapic-generator-kotlin/tree/master)
[![codecov](https://codecov.io/gh/googleapis/gapic-generator-kotlin/branch/master/graph/badge.svg)](https://codecov.io/gh/googleapis/gapic-generator-kotlin)

*Note* This project is a preview. Please try it out and let us know what you think, but there 
are currently no guarantees of any form of stability or support.

## Quick Start

Kgen can be used with [docker](https://www.docker.com/), [gradle](https://gradle.org/), 
or as a [protoc plugin](https://developers.google.com/protocol-buffers/). 

To get started with docker, clone the project and run the following to generate a client for the [example service](example-server).

```bash
$ mkdir my-output 
$ docker run --rm \
             --mount type=bind,source="$(pwd)"/example-server/src/main/proto,target=/proto \
             --mount type=bind,source="$(pwd)"/my-output,target=/generated \
         gcr.io/kotlin-gapic/kgen
```

See the [RUNNING.md](RUNNING.md) for more details, configuration, and command line options.

## Example

A simple "hello world" style example is in the [example-server](example-server)
and [example-client](example-client) directories. Here's how it works:

First, describe the API like this ([complete proto file](example-server/src/main/proto/google/example/hello.proto)):
    
```proto
service HelloService {
  rpc HiThere (HiRequest) returns (HiResponse);
}

message HiRequest {
    string query = 1;
}

message HiResponse {
    string result = 1;
}
```

Next, run Kgen on the proto files and it will produce Kotlin code that you can use to call
the API, like this ([complete example](example-client/src/main/kotlin/example/Client.kt)):
    
```kotlin
// create a client with an insecure channel
val client = HelloServiceClient.fromCredentials(
    channel = ManagedChannelBuilder.forAddress("localhost", 8080)
        .usePlaintext()
        .build()
)

// call the API
val response = client.hiThere(HiRequest {
    query = "Hello!"
}).get()

// print the result
println("The response was: ${response.body.result}")
```

The generator creates three things from the proto files:
1. A client for each `service` declared
1. A type-safe builder for each `message` declared
1. Unit tests for each generated client

Finally, you can add annotations to the proto to customize the way Kgen generates code. For example:

```proto
rpc HiThere (HiRequest) returns (HiResponse) {
  option (google.api.method_signature) = {
    fields: ["result"]
  };
}
```

will change the client so that you can call the example API like this instead:

```kotlin
// call the API
val response = client.hiThere("Hello!")
```

Of course, don't forget to implement the API ([example implementation](example-server/src/main/kotlin/example/ExampleServer.kt)).

You can run this example locally by using gradle:

```bash
$ cd example-server && ./gradlew run
$ cd example-client && ./gradlew run
```

More complex examples, using Google Cloud APIs on Android, can be found in the 
[examples directory](example-api-clients/README.md).

## Configuration

Kgen can be configured to produce Kotlin code that's easy to use in various flavors. See the
[CONFIGURATION.md](CONFIGURATION.md) to learn about these additional features.

## Why Kgen?

Protocol buffers and gRPC have great tool chains, but they do not have first class support for Kotlin and 
they do not provide many configuration options for generated code. Kgen generates idiomatic Kotlin clients
for protobuf APIs and introduces new configuration options to make the code even more enjoyable to use.

Clients generated using Kgen can also take advantage of the Kotlin [API extension library](https://github.com/googleapis/gax-kotlin)
that simplifies common operations like customizing request and response metadata, handling paged responses, and
using client-side gRPC interceptors with with your API.

## Contributing

Contributions to this library are always welcome and highly encouraged.

See the [CONTRIBUTING](CONTRIBUTING.md) documentation for more information on how to get started.

## Versioning

This library is currently a *preview* with no guarantees of stability or support. Please get involved and let us know
if you find it useful and we'll work towards a stable version.

## Disclaimer

This is not an official Google product.
