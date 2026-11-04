package com.larus.nova.dashboard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.larus.nova.R
import com.larus.nova.attestation.DiagnosticPolicy
import java.util.concurrent.TimeUnit

class ExpiryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val p = applicationContext.getSharedPreferences("dashboard", 0)
        if (!p.getBoolean("reminders", false)) return Result.success()
        val expires = p.getLong("rkp_expiry", 0)
        val now = System.currentTimeMillis()
        if (expires == 0L || expires - now > 7L * 86400000) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        if (now - p.getLong("last_reminder", 0) < 86400000) return Result.success()
        val channel = "certificate_expiry"
        if (Build.VERSION.SDK_INT >= 26) applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel, "证书临期提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, DashboardActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, channel).setSmallIcon(R.drawable.ic_info_outline_24)
            .setContentTitle("上次记录的 RKP 证书：${DiagnosticPolicy.expiry(now, expires)}")
            .setContentText("系统可能已轮换证书，请打开 App 重新检测；这不是实时吊销结论。")
            .setContentIntent(intent).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(2201, n) }
            .onSuccess { p.edit().putLong("last_reminder", now).apply() }
        return Result.success()
    }
    companion object {
        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            manager.cancelUniqueWork("expiry-check") // Retire the pre-2.4 component name.
            if (enabled) manager.enqueueUniquePeriodicWork("nova-expiry-check", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ExpiryWorker>(12, TimeUnit.HOURS).build())
            else manager.cancelUniqueWork("nova-expiry-check")
        }
    }
}
