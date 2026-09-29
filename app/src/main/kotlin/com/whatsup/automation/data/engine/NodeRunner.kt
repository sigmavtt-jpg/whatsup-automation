package com.whatsup.automation.data.engine

import android.util.Log

object NodeRunner {
    private const val TAG = "NodeRunner"

    var isNativeLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("node")
            System.loadLibrary("node-runner")
            isNativeLoaded = true
            safeLogI("Native node and node-runner libraries loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            safeLogE("Failed to load native node libraries: ${e.message}")
        } catch (e: Throwable) {
            isNativeLoaded = false
            safeLogE("Unexpected error loading native libraries: ${e.message}")
        }
    }

    private fun safeLogI(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun safeLogE(msg: String) {
        try {
            Log.e(TAG, msg)
        } catch (_: Throwable) {
            System.err.println("[$TAG] $msg")
        }
    }

    @JvmStatic
    external fun startNodeWithArguments(arguments: Array<String>): Int
}
