package com.google.experimental.examples.kotlin.util

import android.os.Handler
import java.util.concurrent.Executor
import android.os.Looper

/** Executor for the main thread */
object MainThreadExecutor : Executor {
    private val handler = Handler(Looper.getMainLooper())

    override fun execute(command: Runnable) {
        handler.post(command)
    }
}

typealias OnMainThread = MainThreadExecutor
