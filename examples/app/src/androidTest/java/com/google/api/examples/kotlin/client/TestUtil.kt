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

import android.support.test.espresso.Espresso
import android.support.test.espresso.assertion.ViewAssertions
import android.support.test.espresso.matcher.ViewMatchers
import android.support.test.espresso.matcher.ViewMatchers.withText
import com.google.experimental.examples.kotlin.R
import org.awaitility.Awaitility
import org.hamcrest.Matchers.not

// waits until the loading text goes away, useful for simple tests
fun awaitApiCall() {
    Awaitility.await().untilAsserted {
        Espresso.onView(ViewMatchers.withId(R.id.text_view))
                .check(ViewAssertions.matches(not(withText(R.string.loading))))
    }
}