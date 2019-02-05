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
import com.google.api.kgax.grpc.BasicInterceptor
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.cloud.language.v1.document
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val TAG = "Demo"

/**
 * Kotlin example showcasing the Language API and a gRPC interceptor.
 */
class LanguageInterceptorActivity : AppCompatActivity(), CoroutineScope {

    lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val client by lazy {
        // create a client using a service account for simplicity
        // do not use service accounts in real applications
        applicationContext.resources.openRawResource(R.raw.sa).use {
            LanguageServiceClient.fromServiceAccount(it)
        }.prepare {
            // add an interceptor
            withInterceptor(BasicInterceptor(
                onMessage = { Log.i(TAG, "A message of type: '${it.javaClass}' was received!") }
            ))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        job = Job()

        val document = document {
            content = "Hi there Joe"
            type = Document.Type.PLAIN_TEXT
        }

        // make two API calls so we can see how the interceptor sees all outbound messages
        val firstResponse = async { client.analyzeEntities(document, EncodingType.UTF8) }
        val secondResponse = async { client.analyzeEntitySentiment(document, EncodingType.UTF8) }

        launch {
            textView.text = "${firstResponse.await().body}\n\n${secondResponse.await().body}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // release all resources
        job.cancel()
        client.shutdownChannel()
    }
}
