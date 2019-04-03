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
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.cloud.language.v1.document
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Kotlin example showcasing metadata using the Language client library.
 */
class LanguageMetadataActivity : AppCompatActivity(), CoroutineScope {

    lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val client by lazy {
        // create a client using a service account for simplicity
        // do not use service accounts in real applications
        applicationContext.resources.openRawResource(R.raw.sa).use {
            LanguageServiceClient.fromServiceAccount(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        job = Job()

        // call the api
        launch {
            val response = client.prepare {
                withMetadata("foo", listOf("1", "2"))
                withMetadata("bar", listOf("a", "b"))
            }.analyzeEntities(document {
                content = "Hi there Joe"
                type = Document.Type.PLAIN_TEXT
            }, EncodingType.UTF8)

            // TODO: fix me
            textView.text = "The API says: ${response}\n\n" /* +
                "with metadata of: ${response.metadata.keys().joinToString(",")}" */
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // release all resources
        job.cancel()
        client.shutdownChannel()
    }
}
