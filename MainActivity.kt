package com.gameboost

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(val pkg: String, val name: String)

fun Modifier.bounce(onClick: () -> Unit) = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f), label = "b")
    this.scale(s).clickable(src, null, onClick = onClick)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme(darkColorScheme()) { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var games by remember { mutableStateOf(Store.games(ctx).toList()) }
    var on by remember { mutableStateOf(GameService.running) }
    var root by remember { mutableStateOf<Boolean?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var usageOk by remember { mutableStateOf(hasUsage(ctx)) }
    LaunchedEffect(Unit) { root = withContext(Dispatchers.IO) { Root.available() } }

    val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.08f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pp")
    val bgc = Brush.verticalGradient(listOf(Color(0xFF0B0D1A), Color(0xFF1A1040)))

    Column(Modifier.fillMaxSize().background(bgc).padding(20.dp).statusBarsPadding()) {
        Text("⚡ GameBoost", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(when (root) { true -> "Root ativo ✔"; false -> "Root não concedido ✖"; else -> "Verificando root…" },
            color = if (root == true) Color(0xFF4CFFB0) else Color(0xFFFF6B6B))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).scale(if (on) pulse else 1f).clip(CircleShape)
                .background(Brush.linearGradient(if (on) listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF)) else listOf(Color(0xFF2A2F4A), Color(0xFF3A3F5A))))
                .bounce {
                    if (!overlayOk || !usageOk) return@bounce
                    if (on) ctx.stopService(Intent(ctx, GameService::class.java))
                    else ctx.startForegroundService(Intent(ctx, GameService::class.java))
                    on = !on
                }, contentAlignment = Alignment.Center) {
                Text(if (on) "ON" else "OFF", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Text(if (on) "Otimização rodando em segundo plano" else "Toque para ativar o Game Turbo",
            color = Color(0xFFB0B8E0), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))

        if (!overlayOk) PermCard("Permitir painel sobre o jogo") {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            overlayOk = Settings.canDrawOverlays(ctx) }
        if (!usageOk) PermCard("Permitir acesso de uso (detectar o jogo)") {
            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); usageOk = hasUsage(ctx) }

        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Meus jogos", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF7C4DFF)).bounce { showAdd = true }
                .padding(horizontal = 16.dp, vertical = 8.dp)) { Text("+ Adicionar", color = Color.White) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(games, key = { it }) { pkg ->
                val label = remember(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                val icon = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x22FFFFFF)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { Image(it, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) }
                    Text(label, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Text("▶", fontSize = 22.sp, color = Color(0xFF00E5FF), modifier = Modifier.bounce {
                        pm.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) } }.padding(8.dp))
                    Text("🗑", fontSize = 20.sp, modifier = Modifier.bounce {
                        games = games - pkg; Store.setGames(ctx, games.toSet()) }.padding(8.dp))
                }
            }
        }
    }

    if (showAdd) {
        var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) {
                val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
                    .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.pkg }.filter { it.pkg != ctx.packageName }.sortedBy { it.name.lowercase() }
            }
        }
        AlertDialog(onDismissRequest = { showAdd = false }, confirmButton = {
            TextButton(onClick = { showAdd = false }) { Text("Fechar") } },
            title = { Text("Escolha o jogo") },
            text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(apps, key = { it.pkg }) { a ->
                    val added = a.pkg in games
                    Row(Modifier.fillMaxWidth().bounce { if (!added) { games = games + a.pkg; Store.setGames(ctx, games.toSet()) } }
                        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ic = remember(a.pkg) { runCatching { pm.getApplicationIcon(a.pkg).toBitmap(72, 72).asImageBitmap() }.getOrNull() }
                        ic?.let { Image(it, null, Modifier.size(36.dp)) }
                        Text(a.name, Modifier.weight(1f).padding(start = 12.dp))
                        if (added) Text("✔", color = Color(0xFF4CFFB0))
                    }
                } } })
    }
}

@Composable
fun PermCard(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FF9800))
        .bounce(onClick).padding(14.dp)) { Text("⚠ $text", color = Color(0xFFFFC107)) }
}

fun hasUsage(c: android.content.Context): Boolean {
    val ops = c.getSystemService(AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
}
