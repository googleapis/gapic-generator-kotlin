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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.api.examples.kotlin.util.AudioEmitter
import com.google.api.kgax.grpc.StreamingCall
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.StreamingRecognizeRequest
import com.google.cloud.speech.v1.StreamingRecognizeResponse
import com.google.cloud.speech.v1.recognitionConfig
import com.google.cloud.speech.v1.streamingRecognitionConfig
import com.google.cloud.speech.v1.streamingRecognizeRequest
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private const val TAG = "Demo"

/**
 * Kotlin example showcasing duplex streaming using the Speech client library.
 */
@ExperimentalCoroutinesApi
class SpeechStreamingActivity : AppCompatActivity(), CoroutineScope {

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var permissionToRecord = false
    private val audioEmitter: AudioEmitter = AudioEmitter()
    private var streams: SpeechStreams? = null

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

        // get permissions
        ActivityCompat.requestPermissions(
            this, PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onResume() {
        super.onResume()

        job = Job()

        // kick-off recording process, if we're allowed
        if (permissionToRecord) {
            launch { streams = transcribe() }
        }
    }

    override fun onPause() {
        super.onPause()

        // ensure mic data stops
        launch {
            audioEmitter.stop()
            streams?.responses?.cancel()
            job.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // release all resources
        client.shutdownChannel()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION ->
                permissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED
        }

        if (!permissionToRecord) {
            Log.e(TAG, "No permission to record - please grant and retry!")
            finish()
        }
    }

    private suspend fun transcribe(): SpeechStreams {
        Log.i(TAG, "Starting talking!")

        // start streaming the data to the server and collect responses
        val streams = client.streamingRecognize(
            streamingRecognitionConfig {
                config = recognitionConfig {
                    languageCode = "en-US"
                    encoding = RecognitionConfig.AudioEncoding.LINEAR16
                    sampleRateHertz = 16000
                }
                interimResults = false
                singleUtterance = false
            })

        // monitor the input stream and send requests as audio data becomes available
        launch(Dispatchers.IO) {
            for (bytes in audioEmitter.start(this)) {
                streams.requests.send(streamingRecognizeRequest {
                    audioContent = bytes
                })
            }
        }

        // handle incoming responses
        launch(Dispatchers.Main) {
            for (response in streams.responses) {
                textView.text = response.toString()
            }
        }

        return streams
    }
}

private typealias SpeechStreams = StreamingCall<StreamingRecognizeRequest, StreamingRecognizeResponse>
