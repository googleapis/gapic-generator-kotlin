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
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.google.api.MonitoredResource
import com.google.api.examples.kotlin.util.MainThread
import com.google.kgax.ServiceAccount
import com.google.kgax.grpc.on
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
        // create a client using a service account for simplicity
        // refer to see MainActivity for more details on how to authenticate
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
        val globalResource = MonitoredResource { type = "global" }

        // ensure we have some logs to read
        val entries = List(40) {
            LogEntry {
                resource = globalResource
                logName = log
                textPayload = "log number: ${it + 1}"
            }
        }

        // write the entries
        client.writeLogEntries(log, globalResource, mapOf(), entries).on(MainThread) {
            success = {
                // the server may respond with an empty set if we immediately try to read the logs
                // that we just wrote - so we wait for a few seconds before proceeding
                Handler().postDelayed({
                    // now, read those entries back
                    val pager = client.listLogEntries(
                            listOf(project), "logName=$log", "timestamp desc", 10)

                    // go through all the logs, one page at a time
                    pager.forEach(MainThread) { page ->
                        for (entry in page.elements) {
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
                }, 5 * 1_000)
            }
            error = { resultText.text = "Error: $it" }
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
