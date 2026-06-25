package com.example.androidllm

import android.app.Application

class AndroidLlmApp : Application() {
    private val controllerDelegate = lazy {
        LocalLlmController(this)
    }

    val controller: LocalLlmController
        get() = controllerDelegate.value

    override fun onTerminate() {
        if (controllerDelegate.isInitialized()) {
            controller.close()
        }
        super.onTerminate()
    }
}
