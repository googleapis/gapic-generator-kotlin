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

package example

import com.google.api.MonitoredResource
import com.google.logging.v2.LogEntry
import com.google.logging.v2.LoggingServiceV2Client
import java.lang.RuntimeException
import java.util.Date

/**
 * Simple example of calling the Logging API
 * with a generated Kotlin gRPC client.
 *
 * Run this example using your service account as follows:
 *
 * ```
 * $ CREDENTIALS=<path_to_your_service_account.json> ./gradlew run --args logging
 * ```
 */
fun loggingExample() {
    val client = LoggingServiceV2Client.fromEnvironment()

    // get the project id
    // in a real app you could use a constant string
    val projectId = System.getenv("PROJECT")
        ?: throw RuntimeException("You must set the PROJECT environment variable to run this example")

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
    println("Writing log entries...")
    client.writeLogEntries(log, globalResource, mapOf(), entries).get()

    // wait a few seconds (if read back immediately the API may not have saved the entries)
    Thread.sleep(5_000)

    // now, read those entries back
    val pager = client.listLogEntries(
        listOf(project), "logName=$log", "timestamp asc", 10
    )

    // go through all the logs, one page at a time
    println("Reading log entries...")
    for (page in pager) {
        for (entry in page.elements) {
            println("log : ${entry.textPayload}")
        }
    }
}
