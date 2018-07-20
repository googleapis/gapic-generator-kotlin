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

package com.google.api.examples.kotlin.util

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.io.InputStream

/**
 * Example of fetching an access token via service account credentials. The token
 * can be used to securely call APIs until it expires.
 *
 * This should *not* be done on a device. Your server should create access tokens
 * and securely transfer them to your mobile application. This is only done here
 * as an example so that no server is needed to run the examples.
 *
 * Service account credentials should *not* be embedded in a mobile application.
 */
class AccessTokens(keyFile: InputStream, scopes: List<String>) {

    // service account credentials
    private val credentials =
            GoogleCredentials.fromStream(keyFile).createScoped(scopes)

    /**
     * Fetch a new token. Once fetched tokens are valid until their expirationTime, which
     * is typically about an hour.
     */
    fun fetchToken(): AccessToken {
        credentials.refresh()
        return credentials.accessToken
    }
}
