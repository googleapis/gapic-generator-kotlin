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

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.TextView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.examples.kotlin.util.onUI
import com.google.auth.oauth2.AccessToken
import com.google.cloud.language.v1.Document
import com.google.cloud.language.v1.EncodingType
import com.google.cloud.language.v1.LanguageServiceClient
import com.google.gson.Gson
import com.squareup.okhttp.Callback
import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import java.io.IOException
import java.util.Calendar

private const val TAG = "SignIn"
private const val RC_SIGN_IN = 100

/**
 * Kotlin example showcasing the Language API and user-level credentials.
 *
 * See the notes on the [AccessTokenFetcher] for important details about how this
 * should be done in real apps.
 */
class LanguageSignInActivity : AppCompatActivity() {

    private var languageClient: LanguageServiceClient? = null
    private lateinit var signInClient: GoogleSignInClient
    private lateinit var authClientId: String
    private lateinit var authClientSecret: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // get the auth related info
        val scopes = LanguageServiceClient.ALL_SCOPES.asScopes()
        application.resources.openRawResource(R.raw.client_config).use { input ->
            val config = input.bufferedReader().use { Gson().fromJson(it, ClientConfig::class.java) }
            authClientId = config.client_id

            // Note: you should not store this in a real app
            authClientSecret = config.client_secret
        }

        // login with GoogleSignIn using the scopes
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(scopes.first(), *scopes)
            .requestServerAuthCode(authClientId)
            .build()

        // build a GoogleSignInClient with the options
        signInClient = GoogleSignIn.getClient(this, signInOptions)
    }

    override fun onStart() {
        super.onStart()

        // sign in
        val signInIntent = signInClient.getSignInIntent()
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // check for sign in result
        if (requestCode == RC_SIGN_IN) {
            try {
                // get the returned auth code to exchange for a token
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val authCode = task.getResult(ApiException::class.java)!!.serverAuthCode!!
                val tokenSource = AccessTokenFetcher(authCode, authClientId, authClientSecret)

                // fetch a token and call the API
                tokenSource.fetch { callAPI(it) }
            } catch (e: ApiException) {
                Log.e(TAG, "Unable to login with user account!", e)
            }
        }
    }

    private fun callAPI(token: AccessToken) {
        val textView: TextView = findViewById(R.id.text_view)

        // create a client with an access token
        languageClient = LanguageServiceClient.fromAccessToken(token)

        // call the API
        languageClient!!.analyzeEntities(Document {
            content = "Hi there Joe"
            type = Document.Type.PLAIN_TEXT
        }, EncodingType.UTF8).onUI {
            success = { textView.text = "The API says: $it" }
            error = { textView.text = "Error: $it" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        languageClient?.shutdownChannel()
    }
}

/**
 * This helper exchanges an auth code for an access token.
 *
 * Note: This should be done by securely sending the [code] to your backend server so that it
 * can generate access tokens that can be used by the device. Do not store client secrets in
 * real applications. This is done here so the example does not require a server to run.
 */
private class AccessTokenFetcher(
    private val code: String,
    private val clientId: String,
    private val clientSecret: String
) {
    fun fetch(onReady: (AccessToken) -> Unit) {
        OkHttpClient().newCall(
            Request.Builder()
                .url("https://www.googleapis.com/oauth2/v4/token")
                .post(
                    FormEncodingBuilder()
                        .add("code", code)
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("redirect_uri", "")
                        .add("grant_type", "authorization_code")
                        .build()
                )
                .build()
        ).enqueue(object : Callback {
            override fun onResponse(response: Response) {
                val token = Gson().fromJson(response.body().string(), TokenResponse::class.java)
                val expiresAt = Calendar.getInstance()
                expiresAt.add(Calendar.SECOND, token.expires_in)
                onReady(AccessToken(token.access_token, expiresAt.time))
            }

            override fun onFailure(request: Request, e: IOException) {
                Log.e(TAG, "failed to fetch access token", e)
            }
        })
    }
}

private data class TokenResponse(
    val access_token: String,
    val expires_in: Int
)

private data class ClientConfig(
    val client_id: String,
    val client_secret: String
)

private fun List<String>.asScopes() = this.map { Scope(it) }.toTypedArray()
