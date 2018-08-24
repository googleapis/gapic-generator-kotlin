# Kgen

Kgen creates Kotlin client libraries from a [protocol buffer](https://developers.google.com/protocol-buffers/docs/proto3) description of an API. 

The clients are currently compatible with gRPC APIs, but we plan to support alternative transports.

## Examples

A simple "hello world" style example is in the `generator/example-server` and `generator/example-client` directories. 

The [example-server](generator/example-server) directory contains the [API implementation](generator/example-client/src/main/kotlin/example/ExampleServer.kt) 
and the [proto describing the API](generator/example-client/src/main/proto/google/example/hello.proto).

The [example-client](generator/example-client) directory is a snapshot containing all of the generated 
code when the generator is run using `generator/example-client/src/main/proto` as the input directory.
It includes the following subdirectories:
   + `client`: A client for each of the API services and various `KotlinBuilders.kt` that may be used as an alternative to the Java builders.
   + `clientTest`: Test code for the clients.
   + `grpc`: gRPC stubs (used interally by the clients).
   + `javalite`: Java message types defined in the API.

More complex examples, using Google Cloud APIs on Android, can be found in the 
[examples directory](examples/README.md).

## Configuration

Kgen currently uses a legacy format based on `.yaml` files for configuration. However, it is being replaced 
with a set of annotations that can be put directly into the `.proto` files describing the API. We do 
not recommend using the legacy configuration, so it's not described here. 

Kgen can be used without any additional configuration :), but you won't be able to use the 
cutomizations described below just yet :(. We will update our documentation when the new format is ready.

### Configuration Options

#### Method Signatures

  + Specify alternative method signature(s) to simplify your API.
      ```kotlin
       // instead of using the signature defined in the proto file:
       fun createUser(request: CreateUserRequest): CreateUserResponse
       
       // generate alternative methods that take multiple or smaller arguments
       // for common use cases.
       fun createUser(userName: String, userLocation: Location): CreateUserResponse
       fun createUser(user: User): CreateUserResponse
      ```

#### Return Type Transformations

  + Paged responses
  + Transparent handling of long running operations using [google.longrunning.Operation](https://github.com/googleapis/googleapis/blob/master/google/longrunning/operations.proto)

#### Example Code

  + Specify values for examples in method documentation.
     ```kotlin
     /**
     * val result = client.hiThere(
     *     HiRequest {
     *         query = "Hey!"
     *     }
     * )
     */
     fun hiThere(request: HiRequest): HiResponse
     ```
#### Test Code

  + Unit tests
  + Functional tests using specified data values

#### Kgax

  + All API methods are invoked using the [Google API extension library for Kotlin](https://github.com/googleapis/gax-kotlin) 
   and can take advantage of it's features, including a simplified interface for streaming methods, metadata access, interceptors, etc. 

## Why Kgen?

Protocol buffers and gRPC have great toolchains, but they do not have first class support for Kotlin and 
they do not provide many configuration options for generated code. Kgen generates ideomatic Kotlin client 
libraries and introduces new configuration options that enable fine-tuning the code to make it easier
to consume an API.

## Contributing

Contributions to this library are always welcome and highly encouraged.

See the [CONTRIBUTING](CONTRIBUTING.md) documentation for more information on how to get started.

## Versioning

This library is currently a *preview* with no guarantees of stability or support. Please get involved and let us know
if you find it useful and we'll work towards a stable version.

## Disclaimer

This is not an official Google product.
