# Configuration

Kgen currently uses a legacy format based on `.yaml` files for configuration. However, it is being replaced 
with a set of annotations that can be put directly into the `.proto` files describing the API. We do 
not recommend using the legacy configuration, so it's not described here. 

Kgen can be used without any additional configuration :), but you won't be able to use the 
cutomizations described below just yet :(. We will update our documentation when the new format is ready.

## Configuration Options

### Method Signatures

  + Specify alternative method signature(s) to simplify your API.
      ```kotlin
       // instead of using the signature defined in the proto file:
       fun createUser(request: CreateUserRequest): CreateUserResponse
       
       // generate alternative methods that take multiple or smaller arguments
       // for common use cases.
       fun createUser(userName: String, userLocation: Location): CreateUserResponse
       fun createUser(user: User): CreateUserResponse
      ```

### Return Type Transformations

  + Paged responses
  + Transparent handling of long running operations using [google.longrunning.Operation](https://github.com/googleapis/googleapis/blob/master/google/longrunning/operations.proto)

### Example Code

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
### Test Code

  + Unit tests
  + Functional tests using specified data values

### Kgax

  + All API methods are invoked using the [Google API extension library for Kotlin](https://github.com/googleapis/gax-kotlin) 
   and can take advantage of it's features, including a simplified interface for streaming methods, metadata access, interceptors, etc. 
