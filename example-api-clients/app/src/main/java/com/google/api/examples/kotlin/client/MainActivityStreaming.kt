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
import android.widget.TextView
import com.google.api.examples.kotlin.util.AudioEmitter
import com.google.api.examples.kotlin.util.MainThread
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest

private const val TAG = "Demo"

/**
 * Kotlin example showcasing duplex streaming using the client library.
 *
 * @author jbolinger
 */
class MainActivityStreaming : AppCompatActivity() {

    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    private var permissionToRecord = false
    private var audioEmitter: AudioEmitter? = null

    private val client by lazy {
        // create a client using a service account for simplicity
        // refer to see MainActivity for more details on how to authenticate
        applicationContext.resources.openRawResource(R.raw.sa).use {
            SpeechClient.fromServiceAccount(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get permissions
        ActivityCompat.requestPermissions(
                this, PERMISSIONS, REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun onResume() {
        super.onResume()

        val textView: TextView = findViewById(R.id.text_view)

        // kick-off recording process, if we're allowed
        if (permissionToRecord) {
            audioEmitter = AudioEmitter()

            // start streaming the data to the server and collect responses
            val stream = client.streamingRecognize(
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
            audioEmitter!!.start { bytes ->
                stream.requests.send(StreamingRecognizeRequest {
                    audioContent = bytes
                })
            }

            // handle incoming responses
            stream.start {
                executor = MainThread
                onNext = { textView.text = it.toString() }
                onError = { Log.e(TAG, "uh oh", it) }
                onCompleted = { Log.i(TAG, "All done!") }
            }
        } else {
            Log.e(TAG, "No permission to record! Please allow and then relaunch the app!")
        }
    }

    override fun onPause() {
        super.onPause()

        // ensure mic data stops
        audioEmitter?.stop()
        audioEmitter = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // cleanup
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