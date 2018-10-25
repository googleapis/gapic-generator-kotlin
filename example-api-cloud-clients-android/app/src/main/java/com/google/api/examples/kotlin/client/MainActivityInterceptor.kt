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
import android.util.Log
import android.widget.TextView
import com.google.api.examples.kotlin.util.MainThread
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.api.kgax.grpc.BasicInterceptor
import com.google.api.kgax.grpc.on

private const val TAG = "Demo"

/**
 * Kotlin example calling the language API and a gRPC interceptor.
 */
class MainActivityInterceptor : AppCompatActivity() {

    private val client by lazy {
        // create a client using a service account for simplicity
        // refer to see MainActivity for more details on how to authenticate
        applicationContext.resources.openRawResource(R.raw.sa).use {
            LanguageServiceClient.fromServiceAccount(it)
        }.prepare {
            withInterceptor(BasicInterceptor(
                    onMessage = { Log.i(TAG, "A message of type: '${it.javaClass}' was received!") }
            ))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView: TextView = findViewById(R.id.text_view)

        val document = Document {
            content = "Hi there Joe"
            type = Document.Type.PLAIN_TEXT
        }

        // make an API call
        client.analyzeEntities(document, EncodingType.UTF8)

        // do a second call so we can see how the interceptor sees all outbound messages
        client.analyzeEntitySentiment(document, EncodingType.UTF8).on(MainThread) {
            success = { textView.text = "The API says: ${it.body}" }
            error = { textView.text = "Error: $it" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        client.shutdownChannel()
    }
}
