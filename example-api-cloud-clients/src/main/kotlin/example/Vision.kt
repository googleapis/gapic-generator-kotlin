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

import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.annotateImageRequest
import com.google.cloud.vision.v1.feature
import com.google.cloud.vision.v1.image
import com.google.common.io.ByteStreams
import com.google.protobuf.ByteString
import kotlinx.coroutines.runBlocking

/**
 * Simple example of calling the Logging API with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ GOOGLE_APPLICATION_CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --args vision
 * ```
 */
fun visionExample() = runBlocking {
    // create a client
    val client = ImageAnnotatorClient.fromEnvironment()

    // get image
    val imageData = Main::class.java.getResourceAsStream("/statue.jpg").use {
        ByteString.copyFrom(ByteStreams.toByteArray(it))
    }

    // call the API
    val result = client.batchAnnotateImages(listOf(
        annotateImageRequest {
            image = image { content = imageData }
            features = listOf(
                feature { type = Feature.Type.FACE_DETECTION },
                feature { type = Feature.Type.LANDMARK_DETECTION }
            )
        }
    ))

    println("The response is: $result")

    // shutdown
    client.shutdownChannel()
}