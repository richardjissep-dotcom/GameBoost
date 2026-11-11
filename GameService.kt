package com.gameboost

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.ServiceCompat

class GameService : Service() {
    companion object { @Volatile var running = false }
    private var active: String? = null
    private lateinit var overlay: Overlay
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (running) return START_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gb", "GameBoost", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "gb").setContentTitle("GameBoost ativo")
            .setContentText("Aguardando você abrir um jogo…")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        running = true
        overlay = Overlay(this)
        Thread {
            while (running) {
                val fg = foreground()
                val games = Store.games(this)
                if (fg != null && fg in games && fg != active) {
                    active = fg
                    Optimizer.boost(this, fg)
                    main.post { overlay.show(fg) }
                } else if (active != null && fg != null && fg != active &&
                    fg != packageName && fg != "com.android.systemui") {
                    val g = active!!; active = null
                    main.post { overlay.hide() }
                    Optimizer.restore(this, g)
                }
                Thread.sleep(2000)
            }
        }.start()
        return START_STICKY
    }

    private fun foreground(): String? {
        val um = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val ev = um.queryEvents(now - 15000, now); val e = UsageEvents.Event(); var last: String? = null
        while (ev.hasNextEvent()) { ev.getNextEvent(e); if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName }
        return last
    }

    override fun onDestroy() {
        running = false
        active?.let { g -> Thread { Optimizer.restore(this, g) }.start() }
        main.post { overlay.hide() }
        super.onDestroy()
    }
}
