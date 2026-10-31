package io.github.devilbike.cratesorter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import kotlin.concurrent.thread

// Runs while the app is working: a notification plus a CPU wake lock keep the process
// and its network requests alive with the screen off. Nothing here touches audio.
// It also runs the MusicBrainz genre lookups natively, so they carry on even if Android
// freezes the page, and resume by themselves if Android restarts the service.
class WorkService : Service() {
    private var lock: PowerManager.WakeLock? = null
    val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (LastFm.key.isBlank()) LastFm.key = getSharedPreferences("genres", MODE_PRIVATE).getString("lfmKey", "") ?: ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            goForeground(statusText())
        } catch (e: Exception) {
            // Android can refuse to restart a foreground service from the background.
            GenreStore.log("could not start background service: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (lock == null) {
            lock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CrateSorter:work")
                .apply { acquire(6 * 60 * 60 * 1000L) }
        }
        when (intent?.action) {
            ACTION_UI_ON -> uiWorking = true
        }
        if (GenreStore.left(this) > 0) startGenreWorker()
        stopIfIdle()
        return if (GenreStore.left(this) > 0) START_STICKY else START_NOT_STICKY
    }

    private fun startGenreWorker() {
        if (worker?.isAlive == true) return
        GenreStore.log("genre worker started, ${GenreStore.left(this)} left")
        worker = thread(name = "genres") {
            var gap = 1200L
            var failures = 0
            while (true) {
                val next = GenreStore.peek(this) ?: break
                val lastFm = next.first.startsWith("lf")
                Thread.sleep(if (lastFm) maxOf(300L, gap / 4) else gap)
                when (val r = if (lastFm) LastFm.lookup(next.first, next.second) else MusicBrainz.lookup(next.second)) {
                    is MusicBrainz.Result.Found -> {
                        GenreStore.put(this, next.first, r.tags)
                        failures = 0
                        gap = maxOf(1200L, gap * 95 / 100)
                    }
                    MusicBrainz.Result.BadKey -> {
                        GenreStore.log("bad Last.fm key, dropping the Last.fm lookups")
                        GenreStore.dropWhere(this) { it.startsWith("lf") }
                    }
                    MusicBrainz.Result.SlowDown -> {
                        gap = minOf(gap * 3 / 2, 5000L)
                        Thread.sleep(5000)
                    }
                    is MusicBrainz.Result.Failed -> {
                        failures++
                        GenreStore.log("\"${next.second}\" failed (${r.why}), attempt $failures")
                        if (failures >= 8) {
                            GenreStore.skip(this, next.first)
                            failures = 0
                        } else {
                            Thread.sleep(if (failures >= 3) 60_000L else 5_000L)
                        }
                    }
                }
                main.post { runCatching { goForeground(statusText()) } }
            }
            GenreStore.log("genre worker finished")
            main.post { stopIfIdle() }
        }
    }

    private fun statusText(): String {
        val left = GenreStore.left(this)
        return if (left > 0) "Looking up tags: $left left. Tap to see progress."
        else "Sorting your Spotify library. Tap to see progress."
    }

    private fun goForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sorting progress", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Crate Sorter is working")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    fun stopIfIdle() {
        if (!uiWorking && worker?.isAlive != true) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // Android 15 limits data-sync services to 6 hours a day.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        lock?.let { if (it.isHeld) it.release() }
        lock = null
        instance = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "work"
        const val ACTION_UI_ON = "ui_on"
        const val ACTION_GENRES = "genres"

        @Volatile var uiWorking = false
        @Volatile private var worker: Thread? = null
        @Volatile private var instance: WorkService? = null

        val running: Boolean get() = worker?.isAlive == true

        fun send(context: Context, action: String) {
            context.startForegroundService(Intent(context, WorkService::class.java).setAction(action))
        }

        // Called without starting the service, because Android may refuse a start from the background.
        fun pageDone() {
            uiWorking = false
            instance?.let { s -> s.main.post { s.stopIfIdle() } }
        }
    }
}
