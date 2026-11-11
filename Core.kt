package com.gameboost

import android.content.Context

object Root {
    /** Executa comandos como root (su). Chame fora da thread principal. */
    fun run(vararg cmds: String): String = try {
        val p = Runtime.getRuntime().exec("su")
        p.outputStream.bufferedWriter().use { w -> cmds.forEach { w.write(it + "\n") }; w.write("exit\n") }
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(); out
    } catch (e: Exception) { "" }
    fun available() = run("id").contains("uid=0")
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("gb", Context.MODE_PRIVATE)
    fun games(c: Context): Set<String> = p(c).getStringSet("games", emptySet())!!
    fun setGames(c: Context, s: Set<String>) = p(c).edit().putStringSet("games", s).apply()
    fun saveGov(c: Context, g: String) = p(c).edit().putString("gov", g).apply()
    fun gov(c: Context) = p(c).getString("gov", "schedutil")!!
}

object Optimizer {
    /** Limpa RAM: cache + fecha apps de terceiros (menos o jogo, este app e o teclado). */
    fun clean(c: Context, game: String) = Root.run(
        "sync; echo 3 > /proc/sys/vm/drop_caches",
        "for p in \$(pm list packages -3 | cut -d: -f2); do " +
        "case \$p in $game|${c.packageName}|*inputmethod*|*keyboard*) ;; *) am force-stop \$p;; esac; done"
    )

    fun performance(c: Context, game: String, on: Boolean) {
        if (on) {
            val g = Root.run("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").trim()
            if (g.isNotEmpty() && g != "performance") Store.saveGov(c, g)
            Root.run(
                "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \$f; done",
                "cmd game mode performance $game",
                "settings put global window_animation_scale 0.5",
                "settings put global transition_animation_scale 0.5",
                "settings put global animator_duration_scale 0.5"
            )
        } else Root.run(
            "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo ${Store.gov(c)} > \$f; done",
            "cmd game mode standard $game",
            "settings put global window_animation_scale 1.0",
            "settings put global transition_animation_scale 1.0",
            "settings put global animator_duration_scale 1.0"
        )
    }

    fun dnd(on: Boolean) = Root.run("settings put global zen_mode ${if (on) 1 else 0}")

    fun boost(c: Context, game: String) { dnd(true); performance(c, game, true); clean(c, game) }
    fun restore(c: Context, game: String) { dnd(false); performance(c, game, false) }
}
