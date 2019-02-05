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

import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.longRunningRecognizeRequest
import com.google.cloud.speech.v1.recognitionAudio
import com.google.cloud.speech.v1.recognitionConfig
import com.google.common.io.ByteStreams
import com.google.protobuf.ByteString
import kotlinx.coroutines.runBlocking

/**
 * Simple example of calling the Logging API with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ GOOGLE_APPLICATION_CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --args speech
 * ```
 */
fun speechExample() = runBlocking {
    // create a client
    val client = SpeechClient.fromEnvironment()

    // get audio
    val audioData = Main::class.java.getResourceAsStream("/audio.raw").use {
        ByteString.copyFrom(ByteStreams.toByteArray(it))
    }

    // call the API
    val operation = client.longRunningRecognize(longRunningRecognizeRequest {
        audio = recognitionAudio {
            content = audioData
        }
        config = recognitionConfig {
            encoding = RecognitionConfig.AudioEncoding.LINEAR16
            sampleRateHertz = 16000
            languageCode = "en-US"
        }
    })

    // wait for the result
    println("The response is: ${operation.await().body}")

    // shutdown
    client.shutdownChannel()
}
