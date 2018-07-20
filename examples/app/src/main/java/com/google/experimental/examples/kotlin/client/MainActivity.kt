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

package com.google.experimental.examples.kotlin.client

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.google.cloud.language.v1.AnalyzeEntitiesResponse
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.experimental.examples.kotlin.R
import com.google.experimental.examples.kotlin.util.AccessTokens
import com.google.kgax.grpc.CallResult

/**
 * Kotlin example calling the language API.
 *
 * This example uses an [com.google.auth.oauth2.AccessToken], which is the preferred
 * method for authentication on mobile devices. However, this example has been simplified
 * so that no server is required. Refer to [AccessTokens] for more details.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView: TextView = findViewById(R.id.text_view)

        applicationContext.resources.openRawResource(R.raw.sa).use {
            val tokenFactory = AccessTokens(it, LanguageServiceClient.ALL_SCOPES)
            ApiTestTask(tokenFactory) {
                textView.text = "The API says: $it"
            }.execute()
        }
    }

    private class ApiTestTask(
            val tokens: AccessTokens,
            val callback: (AnalyzeEntitiesResponse) -> Unit
    ) : AsyncTask<Unit, Unit, CallResult<AnalyzeEntitiesResponse>>() {
        override fun doInBackground(vararg params: Unit): CallResult<AnalyzeEntitiesResponse> {
            // create a client with an access token
            val client = LanguageServiceClient.fromAccessToken(tokens.fetchToken())

            // call the API and shutdown the connection
            try {
                return client.analyzeEntities(Document.newBuilder()
                        .setContent("Hi there Joe")
                        .setType(Document.Type.PLAIN_TEXT)
                        .build(), EncodingType.UTF8).get()
            } finally {
                client.shutdownChannel()
            }
        }

        override fun onPostExecute(result: CallResult<AnalyzeEntitiesResponse>) {
            callback(result.body)
        }
    }
}
