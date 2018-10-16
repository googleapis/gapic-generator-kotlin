# Configuration

Kgen can be used without any additional configuration beyond your proto files. 

However, you can customize the generated code by annotating your proto files with any of
the options described below.

## Configuration Options

### Packaging
  
  + You can provide metadata about your client library that will appear in
    method comments and set global preferences, such as the namespace.
      ```proto
      option (google.api.metadata) = {
          product_name: "My Awesome Library"
          product_uri: "https://github.com/my/project"
          package_namespace: ["com", "my", "project"]
      };
      ```

  + You should also configure the host and port that your back-end service
    will run on so that users do not need to configure it directly.
      ```proto
      service AnnotationService {
          option (google.api.default_host) = "localhost:7469";
          // ...
      }
      ````

### Method Signatures

  + Specify alternative method signature(s) to simplify your API.

      ```proto
      rpc CreateUser (CreateUserRequest) returns (CreateUserResponse) {
          option (google.api.method_signature) = {
              fields: ["user_name", "user_location.country"]
          };
      }

      message CreateUserRequest {
          string user_name = 1;
          UserLocation user_location = 2;
          // ...
      }

      message UserLocation {
          string country = 1;
          // ...
      }
      ```

      ```kotlin
       // In addition to the standard method:
       fun createUser(request: CreateUserRequest): CreateUserResponse
       
       // this overload will also be generated:
       fun createUser(userName: String, country: String): CreateUserResponse
      ```

### Paged Responses

  + Large responses are automatically paged if your message types define a `page_size` 
    and a `page_token` parameter in the request and a `responses` and a `next_page_token`
    parameter in the response. Of course, make sure that your back-end implements the
    paged method.

    ```proto
    rpc MyPagedMethod (PagedRequest) returns (PagedResponse);

    message PagedRequest {
        int32 page_size = 1;
        string page_token = 2;
        bool flag = 3;
    }

    message PagedResponse {
        repeated Element responses = 1;
        string next_page_token = 2;
    }
    ```

    ```kotlin
    // the generated code will return a Pager that wraps the result
    // type and allows the user to make incremental paged requests.
    //
    // You may combine paging with additional method signatures for ease of use.
    fun myPagedMethod(
        request: PagedRequest
    ): Pager<PagedRequest, CallResult<PagedResponse>, Element>
    ```

### Long Running Operations

  + Transparent handling of long running operations using [google.longrunning.Operation](https://github.com/googleapis/googleapis/blob/master/google/longrunning/operations.proto)

    ```proto
    rpc MethodThatTakesForever (MyRequest) returns (google.longrunning.Operation) {
        option (google.api.operation) = {
            response_type: "MyResponse"
            metadata_type: "MyMetadata"
        };
    }
    ```

    ```kotlin
    // The generated code will return a LongRunningCall<MyResponse> instead of a 
    // FutureCall<MyResponse>. This long running call acts similar to a future, but
    // can potentially make multiple round trips to the server and does not 
    // resolve as completed until the operation is complete.
    //
    // Note: the use of this option requires that your back-end follow Google's
    // long running design pattern described in the link above.
    fun methodThatTakesForever(request: MyRequest): LongRunningCall<MyResponse>
    ```

### Kgax

  + All API methods are invoked using the [Google API extension library for Kotlin](https://github.com/googleapis/gax-kotlin) 
   and can take advantage of it's features, including a simplified interface for streaming methods, metadata access, automatic retries, interceptors, etc. 
