package com.gameboost

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.*
import android.widget.*

class Overlay(private val c: Context) {
    private val wm = c.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private fun dp(v: Int) = (v * c.resources.displayMetrics.density).toInt()
    private fun bg(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private var perf = true; private var dnd = true

    private fun btn(text: String, action: (TextView) -> Unit) = TextView(c).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 14f; setPadding(dp(14), dp(10), dp(14), dp(10))
        background = bg(0xFF2A2F4A.toInt(), 12)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(.92f).scaleY(.92f).setDuration(80).start()
                MotionEvent.ACTION_UP -> { v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); action(this) }
                MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).start()
            }; true
        }
    }

    fun show(game: String) {
        if (root != null || !Settings.canDrawOverlays(c)) return
        val ram = TextView(c).apply { setTextColor(0xFF9FB0FF.toInt()); textSize = 12f }
        val panel = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE; alpha = 0f
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = bg(0xF0141728.toInt(), 18)
        }
        fun toast(s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
        panel.addView(ram)
        panel.addView(btn("🧹 Limpar RAM") { Thread { Optimizer.clean(c, game) }.start(); toast("RAM limpa") })
        panel.addView(btn("🚀 Performance: ON") { t -> perf = !perf
            Thread { Optimizer.performance(c, game, perf) }.start()
            t.text = "🚀 Performance: ${if (perf) "ON" else "OFF"}" })
        panel.addView(btn("🔕 Não perturbe: ON") { t -> dnd = !dnd
            Thread { Optimizer.dnd(dnd) }.start()
            t.text = "🔕 Não perturbe: ${if (dnd) "ON" else "OFF"}" })

        val bubble = TextView(c).apply {
            text = "⚡"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7C4DFF.toInt(), 0xFF00E5FF.toInt())).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(120) }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(bubble); addView(panel) }

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                    bubble.animate().scaleX(.85f).scaleY(.85f).setDuration(80).start() }
                MotionEvent.ACTION_MOVE -> { val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) + Math.abs(dy) > 12) moved = true
                    if (moved) { lp.x = ox + dx; lp.y = oy + dy; wm.updateViewLayout(box, lp) } }
                MotionEvent.ACTION_UP -> {
                    bubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!moved) {
                        if (panel.visibility == View.GONE) {
                            val mi = ActivityManager.MemoryInfo()
                            c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
                            ram.text = "RAM livre: ${mi.availMem / 1048576} MB / ${mi.totalMem / 1048576} MB"
                            panel.visibility = View.VISIBLE; panel.animate().alpha(1f).setDuration(180).start()
                        } else panel.animate().alpha(0f).setDuration(150).withEndAction { panel.visibility = View.GONE }.start()
                    }
                }
            }; true
        }
        root = box; wm.addView(box, lp)
    }

    fun hide() { root?.let { try { wm.removeView(it) } catch (_: Exception) {} }; root = null }
}
