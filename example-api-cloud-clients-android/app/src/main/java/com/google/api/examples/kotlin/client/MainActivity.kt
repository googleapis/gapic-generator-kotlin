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

package com.google.api.examples.kotlin.client

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.google.api.examples.kotlin.util.AccessTokens
import com.google.api.examples.kotlin.util.onUI
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient

/**
 * Kotlin example calling the language API.
 *
 * This example uses an [com.google.auth.oauth2.AccessToken], which is the preferred
 * method for authentication on mobile devices. However, this example has been simplified
 * so that no server is required. Refer to [AccessTokens] for more details.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var client: LanguageServiceClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView: TextView = findViewById(R.id.text_view)

        // get a source for access tokens
        // Note: access tokens should be generated on a back end server
        // and securely sent to an application (it's done here to keep the example simple)
        val tokenFactory = applicationContext.resources.openRawResource(R.raw.sa).use {
            AccessTokens(it, LanguageServiceClient.ALL_SCOPES)
        }

        // create a client with an access token
        client = LanguageServiceClient.fromAccessToken(tokenFactory.fetchToken())

        // call the API
        client.analyzeEntities(Document {
            content = "Hi there Joe"
            type = Document.Type.PLAIN_TEXT
        }, EncodingType.UTF8).onUI {
            success = { textView.text = "The API says: $it" }
            error = { textView.text = "Error: $it" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        client.shutdownChannel()
    }
}
