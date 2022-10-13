package com.clover.studio.exampleapp.utils.helpers

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

object AppLifecycleManager : LifecycleEventObserver {
    var isInForeground = true

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                isInForeground = false
            }
            Lifecycle.Event.ON_START -> {
                isInForeground = true
            }
            else -> {}
        }
    }
}