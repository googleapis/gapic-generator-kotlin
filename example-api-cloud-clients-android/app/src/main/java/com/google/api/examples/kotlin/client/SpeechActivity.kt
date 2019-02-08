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
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.longRunningRecognizeRequest
import com.google.cloud.speech.v1.recognitionAudio
import com.google.cloud.speech.v1.recognitionConfig
import com.google.common.io.ByteStreams
import com.google.protobuf.ByteString
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Kotlin example showcasing long running operations using the Speech client library.
 */
class SpeechActivity : AppCompatActivity(), CoroutineScope {

    lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private val client by lazy {
        // create a client using a service account for simplicity
        // do not use service accounts in real applications
        applicationContext.resources.openRawResource(R.raw.sa).use {
            SpeechClient.fromServiceAccount(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        job = Job()

        launch {
            // get audio
            val audioData = applicationContext.resources.openRawResource(R.raw.audio).use {
                ByteString.copyFrom(ByteStreams.toByteArray(it))
            }

            // start a long running operation
            val lro = client.longRunningRecognize(longRunningRecognizeRequest {
                audio = recognitionAudio {
                    content = audioData
                }
                config = recognitionConfig {
                    encoding = RecognitionConfig.AudioEncoding.LINEAR16
                    sampleRateHertz = 16000
                    languageCode = "en-US"
                }
            })

            // wait for the result and update the UI
            val response = lro.await()
            textView.text = "The API says: ${response.body}\n via operation: ${lro.operation?.name}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // release all resources
        job.cancel()
        client.shutdownChannel()
    }
}
