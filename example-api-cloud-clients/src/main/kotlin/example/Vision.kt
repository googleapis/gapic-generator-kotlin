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

package example

import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.features
import com.google.common.io.ByteStreams
import com.google.kgax.grpc.on
import com.google.protobuf.ByteString

/**
 * Simple example of calling the Logging API with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --args vision
 * ```
 */
fun visionExample() {
    // create a client
    val client = ImageAnnotatorClient.fromEnvironment()

    // get image
    // get audio
    val imageData = Main::class.java.getResourceAsStream("/statue.jpg").use {
        ByteString.copyFrom(ByteStreams.toByteArray(it))
    }

    // call the API
    val future = client.batchAnnotateImages(listOf(
        AnnotateImageRequest {
            image = Image { content = imageData }
            features = listOf(
                Feature { type = Feature.Type.FACE_DETECTION },
                Feature { type = Feature.Type.LANDMARK_DETECTION }
            )
        }
    ))

    // get the result
    // Note: you may use future.get() here (blocking) instead of .on (non-blocking)
    // .on is used here since the other examples use .get()
    future.on {
        success = { println("The response is: ${it.body}") }
    }

    // block since the async .on method was used
    Thread.sleep(5_000)

    // shutdown
    client.shutdownChannel()
}