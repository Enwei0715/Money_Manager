package com.enwei.moneymanager.notify

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/** Removes the persistent quick-add notification left by older app versions. */
object QuickAdd {
    private const val LEGACY_ID = 7301

    fun cancelLegacy(context: Context) {
        NotificationManagerCompat.from(context).cancel(LEGACY_ID)
    }
}
