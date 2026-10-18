package com.trendscout.app.notify

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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trendscout.app.MainActivity
import com.trendscout.app.R
import com.trendscout.app.TrendScoutApp
import com.trendscout.app.data.db.NotifiedEntity
import com.trendscout.app.data.prefs.NotifyKind
import com.trendscout.core.feed.FeedEngine
import com.trendscout.core.feed.FeedQuery
import com.trendscout.core.feed.GeoMode
import com.trendscout.core.model.linksTo
import com.trendscout.core.scoring.ScoreLabel
import java.util.concurrent.TimeUnit

/**
 * Periodic check. Works only with data the app legitimately has: connected feeds (synced here) and
 * the local library. It never scrapes TikTok.
 */
class TrendCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as TrendScoutApp).container
        val s = c.settings.current()
        if (!s.notifyEnabled) return Result.success()

        val prefs = applicationContext.getSharedPreferences("worker", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRun = prefs.getLong("last_run", now - s.notifyFrequencyHours * 3_600_000L)

        if (s.feeds.any { it.enabled }) runCatching { c.feedSync.syncAll(now) }

        val lib = c.library.snapshotLibrary()
        val messages = mutableListOf<Pair<String, String>>()
        val res = applicationContext.resources
        val countries = s.notifyCountries

        val entries = FeedEngine.build(
            lib.videos, lib.trends,
            FeedQuery(countries = countries, geoMode = GeoMode.EITHER, categories = s.notifyCategories),
            now,
        )

        if (NotifyKind.NEW_FAST in s.notifyKinds) {
            val fresh = entries.filter { it.score.label == ScoreLabel.PROMISING && markNew(c, "fast:${it.video.id}", now) }
            if (fresh.isNotEmpty()) {
                messages += res.getString(R.string.notif_new_fast_title, fresh.size) to
                    fresh.take(3).joinToString(" · ") { it.video.title.ifBlank { it.video.authorHandle ?: it.video.url }.take(40) }
            }
        }
        if (NotifyKind.STAT_JUMP in s.notifyKinds) {
            val jumps = entries.filter { e ->
                val acc = e.metrics.acceleration ?: return@filter false
                val at = e.metrics.lastWindow?.toAt ?: return@filter false
                acc >= 2.0 && at > lastRun && markNew(c, "jump:${e.video.id}:$at", now)
            }
            if (jumps.isNotEmpty()) {
                messages += res.getString(R.string.notif_jump_title, jumps.size) to
                    jumps.take(3).joinToString(" · ") { it.video.title.ifBlank { it.video.url }.take(40) }
            }
        }
        val saved = lib.trends.filter { it.saved && !it.used }
        if (NotifyKind.SPREAD in s.notifyKinds && saved.isNotEmpty()) {
            val spreads = mutableListOf<String>()
            for (t in saved) {
                val obs = t.popularIn + lib.videos.filter { it.linksTo(t) }.flatMap { it.popularIn }
                obs.filter { it.observedAt > lastRun && (countries.isEmpty() || it.countryCode in countries) }
                    .map { it.countryCode }.distinct()
                    .filter { markNew(c, "spread:${t.id}:$it", now) }
                    .forEach { spreads += "${t.name} → $it" }
            }
            if (spreads.isNotEmpty()) messages += res.getString(R.string.notif_spread_title) to spreads.take(5).joinToString(", ")
        }
        if (NotifyKind.SAVED_TAGS in s.notifyKinds && saved.isNotEmpty()) {
            val newVideos = lib.videos.filter { v -> v.addedAt > lastRun && saved.any { v.linksTo(it) } && markNew(c, "saved:${v.id}", now) }
            if (newVideos.isNotEmpty()) {
                messages += res.getString(R.string.notif_saved_title, newVideos.size) to
                    saved.filter { t -> newVideos.any { it.linksTo(t) } }.joinToString(", ") { it.name }
            }
        }
        messages.forEachIndexed { i, (title, text) -> Notifier.show(applicationContext, 100 + i, title, text) }
        prefs.edit().putLong("last_run", now).apply()
        c.db.notified().prune(now - 60L * 86_400_000L)
        return Result.success()
    }

    private suspend fun markNew(c: com.trendscout.app.AppContainer, key: String, now: Long): Boolean {
        if (c.db.notified().exists(key) > 0) return false
        c.db.notified().insert(NotifiedEntity(key, now))
        return true
    }

    companion object {
        private const val NAME = "trend_check"

        fun schedule(context: Context, enabled: Boolean, hours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<TrendCheckWorker>(hours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}

object Notifier {
    const val CHANNEL = "trends"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
