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

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.google.api.MonitoredResource
import com.google.experimental.examples.kotlin.R
import com.google.experimental.examples.kotlin.util.OnMainThread
import com.google.kgax.ServiceAccount
import com.google.kgax.grpc.enqueue
import com.google.logging.v2.LogEntry
import com.google.logging.v2.LoggingServiceV2Client
import java.util.Date

/**
 * Kotlin example showcasing paging.
 *
 * @author jbolinger
 */
class MainActivityPaging : AppCompatActivity() {

    private val client by lazy {
        applicationContext.resources.openRawResource(R.raw.sa).use {
            LoggingServiceV2Client.fromServiceAccount(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val resultText: TextView = findViewById(R.id.text_view)

        // get the project id
        // in a real app you wouldn't need to do this and could use a constant string
        val projectId = applicationContext.resources.openRawResource(R.raw.sa).use {
            ServiceAccount.getProjectFromKeyFile(it)
        }

        // resources to use
        val project = "projects/$projectId"
        val log = "$project/logs/testLog-${Date().time}"
        val resource = MonitoredResource.newBuilder().setType("global").build()

        // ensure we have some logs to read
        val entries = List(40) {
            LogEntry.newBuilder()
                    .setResource(MonitoredResource.newBuilder().setType("global").build())
                    .setLogName(log)
                    .setTextPayload("log number: ${it + 1}")
                    .build()
        }

        // write the entries
        client.writeLogEntries(log, resource, mapOf(), entries).enqueue {
            // the server may respond with an empty set if we immediately try to read the logs
            // that we just wrote - so we wait for a few seconds before proceeding
            Handler().postDelayed({
                // now, read those entries back
                val pager = client.listLogEntries(
                        listOf(project), "logName=$log", "timestamp desc", 10)

                // go through all the logs, one page at a time
                pager.forEach(OnMainThread) {
                    for (entry in it.elements) {
                        resultText.text = "${resultText.text}\nlog : ${entry.textPayload}"
                    }
                }

                // Note: you may use the pager as a normal iterator, as shown below,
                //       but avoid doing it from the main thread
                // for (page in pager) {
                //    for (entry in page.elements) {
                //        // ...
                //    }
                // }
            }, 2 * 1000)
        }

        // prepare for the results
        resultText.text = "The logs are:\n"
    }

    override fun onDestroy() {
        super.onDestroy()

        // clean up
        client.shutdownChannel()
    }
}
