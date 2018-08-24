// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package google.example

import com.google.kgax.grpc.ClientCallOptions
import com.google.kgax.grpc.FutureCall
import com.google.kgax.grpc.GrpcClientStub
import com.google.longrunning.OperationsGrpc.OperationsFutureStub
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import google.example.HelloServiceGrpc.HelloServiceFutureStub
import google.example.HelloServiceGrpc.HelloServiceStub
import io.grpc.ManagedChannel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HelloServiceClientTest {
    val streamingStub: GrpcClientStub<HelloServiceStub> = mock()

    val futureStub: GrpcClientStub<HelloServiceFutureStub> = mock()

    val operationsStub: GrpcClientStub<OperationsFutureStub> = mock()

    val channel: ManagedChannel = mock()

    val options: ClientCallOptions = mock()

    @BeforeTest
    fun resetMocks() {
        reset(streamingStub, futureStub, operationsStub, channel, options)
    }

    fun getClient(): HelloServiceClient =
        HelloServiceClient.fromStubs(object : HelloServiceClient.Stubs.Factory {
            override fun create(channel: ManagedChannel, options: ClientCallOptions) =
                HelloServiceClient.Stubs(streamingStub, futureStub, operationsStub)
        }, channel, options)

    @Test
    fun testHiThere() {
        val theRequest: HiRequest = mock()
        val future: FutureCall<HiResponse> = mock()
        whenever(futureStub.executeFuture<HiResponse>(any())).thenReturn(future)

        val client = getClient()
        val result = client.hiThere(theRequest)

        assertEquals(future, result)
        verify(futureStub).executeFuture<HiResponse>(check {
            val mock: HelloServiceFutureStub = mock()
            it(mock)
            verify(mock).hiThere(eq(theRequest))
        })
    }
}
