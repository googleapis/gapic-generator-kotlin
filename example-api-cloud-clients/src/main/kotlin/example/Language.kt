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

import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.LanguageServiceClient
import kotlinx.coroutines.runBlocking

/**
 * Simple example of calling the Natural Language API with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ GOOGLE_APPLICATION_CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --language
 * ```
 */
fun languageExample() = runBlocking {
    // create a client
    val client = LanguageServiceClient.fromEnvironment()

    // call the API
    val result = client.analyzeSentiment(Document {
        content = "Let's see what this API can do. It's great! Right?"
        type = Document.Type.PLAIN_TEXT
    })

    // print the result
    println("The response was: ${result.body}")

    // shutdown
    client.shutdownChannel()
}
