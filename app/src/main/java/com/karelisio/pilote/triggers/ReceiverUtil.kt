package com.karelisio.pilote.triggers

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs [block] then keeps the receiver alive a few seconds so the engine can apply changes. */
fun CoroutineScope.launchReceiver(pending: BroadcastReceiver.PendingResult, block: suspend () -> Unit) {
    launch {
        try {
            runCatching { block() }
            delay(4_000)
        } finally {
            pending.finish()
        }
    }
}
