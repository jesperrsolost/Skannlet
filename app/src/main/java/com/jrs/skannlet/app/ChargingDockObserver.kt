package com.jrs.skannlet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun ChargingDockObserver(onChargingChanged: (Boolean) -> Unit) {
    val context = LocalContext.current.applicationContext
    val currentOnChargingChanged by rememberUpdatedState(onChargingChanged)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                currentOnChargingChanged(isCharging)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}

internal class ChargingSessionTracker {
    private var lastObservedCharging: Boolean? = null

    var userSelectionRequired: Boolean = false
        private set

    fun onChargingChanged(isCharging: Boolean): Boolean {
        if (isCharging && lastObservedCharging != true) {
            userSelectionRequired = true
        }
        lastObservedCharging = isCharging
        return userSelectionRequired
    }

    fun acknowledgeUserSelection() {
        userSelectionRequired = false
    }
}
