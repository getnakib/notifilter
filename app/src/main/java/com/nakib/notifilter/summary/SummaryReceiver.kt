package com.nakib.notifilter.summary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nakib.notifilter.R
import com.nakib.notifilter.data.NotificationDbHelper

/** Posts the 9 pm daily recap: how many notifications came in, how many
 *  were spam. This is NotiFilter's own summary, not another app's content. */
class SummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val db = NotificationDbHelper(context)
        val all = db.all()
        if (all.isEmpty()) return
        val spam = all.count { it.status == "BLOCKED" }
        val real = all.size - spam

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("NotiFilter daily summary")
            .setContentText("$real shown · $spam blocked today")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_summary"
        const val NOTIFICATION_ID = 9001
    }
}
