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
import com.google.api.examples.kotlin.util.OnMainThread
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.kgax.grpc.enqueue

/**
 * Kotlin example showcasing metadata using the client library.
 *
 * @author jbolinger
 */
class MainActivityMetadata : AppCompatActivity() {

    private val client by lazy {
        // create a client using a service account for simplicity
        // refer to see MainActivity for more details on how to authenticate
        applicationContext.resources.openRawResource(R.raw.sa).use {
            LanguageServiceClient.fromServiceAccount(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView: TextView = findViewById(R.id.text_view)

        // call the api
        client.prepare {
            withMetadata("foo", listOf("1", "2"))
            withMetadata("bar", listOf("a", "b"))
        }.analyzeEntities(Document {
            content = "Hi there Joe"
            type = Document.Type.PLAIN_TEXT
        }, EncodingType.UTF8).enqueue(OnMainThread) {
            textView.text = "The API says: ${it.body}\n\n" +
                    "with metadata of: ${it.metadata.keys().joinToString(",")}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        client.shutdownChannel()
    }
}