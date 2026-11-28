package com.lichader.hipdanext.ui

import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Lets Java screens collect a ViewModel's flow while [LifecycleOwner] is started. */
object FlowObservers {

    @JvmStatic
    fun <T> collect(owner: LifecycleOwner, flow: Flow<T>, consumer: Consumer<T>) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { consumer.accept(it) } }
        }
    }
}
