package com.google.api.examples.kotlin.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.api.examples.kotlin.util.AudioEmitter
import com.google.cloud.dialogflow.v2.AudioEncoding
import com.google.cloud.dialogflow.v2.InputAudioConfig
import com.google.cloud.dialogflow.v2.QueryInput
import com.google.cloud.dialogflow.v2.SessionsClient
import com.google.cloud.dialogflow.v2.StreamingDetectIntentRequest
import com.google.cloud.dialogflow.v2.StreamingDetectIntentResponse
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_dialogflow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.util.UUID
import kotlin.coroutines.CoroutineContext

private const val TAG = "Demo"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

/**
 * Kotlin example showcasing the Dialogflow API.
 *
 * You must first setup a project following this guide:
 * https://cloud.google.com/dialogflow-enterprise/docs/quickstart-client-libraries
 *
 * Run the app and say "start stopwatch" or "stop stopwatch" to start and stop the stopwatch.
 * (Tip: replace the phrases "start stopwatch" with "start" when you setup the intents and
 *       you don't need to speak as much!)
 */
@ExperimentalCoroutinesApi
class DialogflowActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var permissionToRecord = false
    private val audioEmitter: AudioEmitter = AudioEmitter()
    private var stopwatchRunning = true
    private var stopwatchValue = 1

    private val client by lazy {
        // create a client using a service account for simplicity
        // do not use service accounts in real applications
        applicationContext.resources.openRawResource(R.raw.sa).use {
            SessionsClient.fromServiceAccount(it)
        }
    }

    private val projectId by lazy {
        // lookup the GCP project id from the service account
        applicationContext.resources.openRawResource(R.raw.sa).use {
            Gson().fromJson(InputStreamReader(it), ServiceAccountJson::class.java).project_id
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialogflow)

        // get permissions
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION
        )
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

    override fun onResume() {
        super.onResume()

        job = Job()

        // kickoff the stopwatch
        launch {
            while (isActive) {
                if (stopwatchRunning) {
                    clockTextView.text = "$stopwatchValue"
                    stopwatchValue++
                }
                delay(1_000)
            }
        }

        // listen for speech commands
        launch {
            while (isActive) {
                // capture some audio and take the last detected command
                for (action in captureAudio(UUID.randomUUID().toString(), 5_000)) {
                    stopwatchRunning = when (action) {
                        StopwatchAction.Start -> true
                        StopwatchAction.Stop -> false
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // stop
        launch {
            audioEmitter.stop()
            job.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // release all resources
        client.shutdownChannel()
    }

    private suspend fun captureAudio(sessionId: String, duration: Long = 50_000) = produce {
        val streams = client.streamingDetectIntent(
            "projects/$projectId/agent/sessions/$sessionId",
            QueryInput {
                audioConfig = InputAudioConfig {
                    languageCode = "en-US"
                    audioEncoding = AudioEncoding.AUDIO_ENCODING_LINEAR_16
                    sampleRateHertz = 16000
                }
            })

        // pipe audio to the server
        launch(Dispatchers.IO) {
            for (bytes in audioEmitter.start(this)) {
                streams.requests.send(StreamingDetectIntentRequest {
                    inputAudio = bytes
                })
            }
        }

        // collect responses from the server
        launch(Dispatchers.IO) {
            for (response in streams.responses) {
                Log.i(TAG, "Dialogflow response: $response")

                // filter response and send any detected intents as actions
                response.asStopwatchAction()?.let { send(it) }
            }
        }

        // shutdown after a short duration
        delay(duration)
        audioEmitter.stop()
        streams.requests.close()
    }
}

private enum class StopwatchAction {
    Start, Stop
}

private fun StreamingDetectIntentResponse.asStopwatchAction() =
    when (this.queryResult?.action) {
        "start" -> StopwatchAction.Start
        "stop" -> StopwatchAction.Stop
        else -> null
    }

private class ServiceAccountJson(val project_id: String)