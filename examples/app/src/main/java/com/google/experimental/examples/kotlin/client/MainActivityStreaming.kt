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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.TextView
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.StreamingRecognitionConfig
import com.google.cloud.speech.v1.StreamingRecognizeRequest
import com.google.experimental.examples.kotlin.R
import com.google.experimental.examples.kotlin.util.AudioEmitter
import com.google.experimental.examples.kotlin.util.OnMainThread

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
    private var audioThread: HandlerThread? = null
    private var audioEmitter: AudioEmitter? = null

    private val client by lazy {
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

        // thread for audio processing
        audioThread = HandlerThread("AudioThread")
        audioThread!!.start()
    }

    override fun onResume() {
        super.onResume()

        val textView: TextView = findViewById(R.id.text_view)

        // kick-off recording process, if we're allowed
        if (permissionToRecord) {
            audioEmitter = AudioEmitter(audioThread!!.looper)

            // start streaming the data to the server and collect responses
            val stream = client.streamingRecognize(
                    StreamingRecognitionConfig.newBuilder()
                            .setConfig(RecognitionConfig.newBuilder()
                                    .setLanguageCode("en-US")
                                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                    .setSampleRateHertz(16000))
                            .setInterimResults(false)
                            .setSingleUtterance(false)
                            .build())

            // monitor the input stream and send requests as audio data becomes available
            audioEmitter!!.start { bytes ->
                stream.requests.send(StreamingRecognizeRequest.newBuilder()
                        .setAudioContent(bytes)
                        .build())
            }

            // handle incoming responses
            stream.responses.executor = OnMainThread
            stream.responses.onNext = { textView.text = it.toString() }
            stream.responses.onError = { Log.e(TAG, "uh oh", it) }
            stream.responses.onCompleted = { Log.i(TAG, "All done!") }
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
        audioThread?.quit()
        client.shutdownChannel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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