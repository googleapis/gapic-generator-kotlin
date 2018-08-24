package google.example;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/** */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.10.0)",
    comments = "Source: google/example/hello.proto")
public final class HelloServiceGrpc {

  private HelloServiceGrpc() {}

  public static final String SERVICE_NAME = "google.example.HelloService";

  // Static method descriptors that strictly reflect the proto.
  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  @java.lang.Deprecated // Use {@link #getHiThereMethod()} instead.
  public static final io.grpc.MethodDescriptor<google.example.HiRequest, google.example.HiResponse>
      METHOD_HI_THERE = getHiThereMethodHelper();

  private static volatile io.grpc.MethodDescriptor<
          google.example.HiRequest, google.example.HiResponse>
      getHiThereMethod;

  @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/1901")
  public static io.grpc.MethodDescriptor<google.example.HiRequest, google.example.HiResponse>
      getHiThereMethod() {
    return getHiThereMethodHelper();
  }

  private static io.grpc.MethodDescriptor<google.example.HiRequest, google.example.HiResponse>
      getHiThereMethodHelper() {
    io.grpc.MethodDescriptor<google.example.HiRequest, google.example.HiResponse> getHiThereMethod;
    if ((getHiThereMethod = HelloServiceGrpc.getHiThereMethod) == null) {
      synchronized (HelloServiceGrpc.class) {
        if ((getHiThereMethod = HelloServiceGrpc.getHiThereMethod) == null) {
          HelloServiceGrpc.getHiThereMethod =
              getHiThereMethod =
                  io.grpc.MethodDescriptor
                      .<google.example.HiRequest, google.example.HiResponse>newBuilder()
                      .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                      .setFullMethodName(
                          generateFullMethodName("google.example.HelloService", "HiThere"))
                      .setSampledToLocalTracing(true)
                      .setRequestMarshaller(
                          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                              google.example.HiRequest.getDefaultInstance()))
                      .setResponseMarshaller(
                          io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                              google.example.HiResponse.getDefaultInstance()))
                      .build();
        }
      }
    }
    return getHiThereMethod;
  }

  /** Creates a new async stub that supports all call types for the service */
  public static HelloServiceStub newStub(io.grpc.Channel channel) {
    return new HelloServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static HelloServiceBlockingStub newBlockingStub(io.grpc.Channel channel) {
    return new HelloServiceBlockingStub(channel);
  }

  /** Creates a new ListenableFuture-style stub that supports unary calls on the service */
  public static HelloServiceFutureStub newFutureStub(io.grpc.Channel channel) {
    return new HelloServiceFutureStub(channel);
  }

  /** */
  public abstract static class HelloServiceImplBase implements io.grpc.BindableService {

    /** */
    public void hiThere(
        google.example.HiRequest request,
        io.grpc.stub.StreamObserver<google.example.HiResponse> responseObserver) {
      asyncUnimplementedUnaryCall(getHiThereMethodHelper(), responseObserver);
    }

    @java.lang.Override
    public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
              getHiThereMethodHelper(),
              asyncUnaryCall(
                  new MethodHandlers<google.example.HiRequest, google.example.HiResponse>(
                      this, METHODID_HI_THERE)))
          .build();
    }
  }

  /** */
  public static final class HelloServiceStub extends io.grpc.stub.AbstractStub<HelloServiceStub> {
    private HelloServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HelloServiceStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloServiceStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HelloServiceStub(channel, callOptions);
    }

    /** */
    public void hiThere(
        google.example.HiRequest request,
        io.grpc.stub.StreamObserver<google.example.HiResponse> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getHiThereMethodHelper(), getCallOptions()),
          request,
          responseObserver);
    }
  }

  /** */
  public static final class HelloServiceBlockingStub
      extends io.grpc.stub.AbstractStub<HelloServiceBlockingStub> {
    private HelloServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HelloServiceBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HelloServiceBlockingStub(channel, callOptions);
    }

    /** */
    public google.example.HiResponse hiThere(google.example.HiRequest request) {
      return blockingUnaryCall(getChannel(), getHiThereMethodHelper(), getCallOptions(), request);
    }
  }

  /** */
  public static final class HelloServiceFutureStub
      extends io.grpc.stub.AbstractStub<HelloServiceFutureStub> {
    private HelloServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private HelloServiceFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HelloServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HelloServiceFutureStub(channel, callOptions);
    }

    /** */
    public com.google.common.util.concurrent.ListenableFuture<google.example.HiResponse> hiThere(
        google.example.HiRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getHiThereMethodHelper(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HI_THERE = 0;

  private static final class MethodHandlers<Req, Resp>
      implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
          io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final HelloServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(HelloServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_HI_THERE:
          serviceImpl.hiThere(
              (google.example.HiRequest) request,
              (io.grpc.stub.StreamObserver<google.example.HiResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (HelloServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor =
              result =
                  io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
                      .addMethod(getHiThereMethodHelper())
                      .build();
        }
      }
    }
    return result;
  }
}
