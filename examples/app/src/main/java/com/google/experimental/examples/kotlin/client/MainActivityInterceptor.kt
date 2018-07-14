package com.google.experimental.examples.kotlin.client

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.TextView
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.experimental.examples.kotlin.R
import com.google.experimental.examples.kotlin.util.OnMainThread
import com.google.kgax.grpc.BasicInterceptor
import com.google.kgax.grpc.enqueue

private const val TAG = "Demo"

/**
 * Kotlin example calling the language API and a gRPC interceptor.
 */
class MainActivityInterceptor : AppCompatActivity() {

    private val client by lazy {
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

        val document = Document.newBuilder()
                .setContent("Hi there Joe")
                .setType(Document.Type.PLAIN_TEXT)
                .build()

        // make an API call
        client.analyzeEntities(document, EncodingType.UTF8)

        // do a second call so we can see how the interceptor sees all outbound messages
        client.analyzeEntitySentiment(document, EncodingType.UTF8)
                .enqueue(OnMainThread) { textView.text = "The API says: ${it.body}" }
    }

    override fun onDestroy() {
        super.onDestroy()

        client.shutdownChannel()
    }
}
