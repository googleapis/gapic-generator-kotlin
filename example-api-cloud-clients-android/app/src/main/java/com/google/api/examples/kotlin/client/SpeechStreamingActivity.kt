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
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest
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
    private var audioEmitter: AudioEmitter? = null

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
            audioEmitter = AudioEmitter()

            launch { transcribe() }
        } else {
            Log.e(TAG, "No permission to record! Please allow and then relaunch the app!")
        }
    }

    private suspend fun transcribe() {
        Log.i(TAG, "Starting talking!")

        // start streaming the data to the server and collect responses
        val streams = client.streamingRecognize(
            StreamingRecognitionConfig {
                config = RecognitionConfig {
                    languageCode = "en-US"
                    encoding = RecognitionConfig.AudioEncoding.LINEAR16
                    sampleRateHertz = 16000
                }
                interimResults = false
                singleUtterance = false
            })

        // monitor the input stream and send requests as audio data becomes available
        launch(Dispatchers.IO) {
            for (bytes in audioEmitter!!.start(this)) {
                streams.requests.send(StreamingRecognizeRequest {
                    audioContent = bytes
                })
            }
        }

        // handle incoming responses
        for (response in streams.responses) {
            textView.text = response.toString()
        }

        Log.i(TAG, "All done - stop talking!")
    }

    override fun onPause() {
        super.onPause()

        // ensure mic data stops
        launch {
            audioEmitter?.stop()
            audioEmitter = null
        }

        // stop streams
        job.cancel()
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
            finish()
        }
    }
}