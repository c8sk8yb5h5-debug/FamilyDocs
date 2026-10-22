package com.octopus.familydocs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private val ScreenBg = Color(0xFF202122)
private val HeaderBg = Color(0xFF3A3B3D)
private val Ink = Color(0xFF24292A)
private val Pastels = listOf(Color(0xFF9ADDE7), Color(0xFF9FE8D3), Color(0xFFFFE5A5), Color(0xFFEBC9E0), Color(0xFFF2F0E7))
private data class Doc(val name: String, val path: String, val category: String, val owner: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(32, 33, 34)
        window.navigationBarColor = android.graphics.Color.rgb(32, 33, 34)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = ScreenBg, surface = HeaderBg)) { FamilyHome() } }
        checkForUpdates(showUpToDate = false)
    }

    private fun checkForUpdates(showUpToDate: Boolean) {
        if (showUpToDate) Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show()
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.github.com/repos/c8sk8yb5h5-debug/FamilyDocs/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "FamilyDocs-Android")
                }
                if (connection.responseCode != 200) throw IllegalStateException("GitHub API HTTP ${connection.responseCode}")
                val json = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
                val release = JSONObject(json)
                val tag = release.optString("tag_name", "")
                val latest = tag.removePrefix("v")
                val notes = release.optString("body", "此版本没有更新说明。")
                val assets = release.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name") == "FamilyDocs.apk") {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
                val current = BuildConfig.VERSION_NAME
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (compareVersions(latest, current) > 0 && apkUrl.isNotBlank()) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("发现新版本 $latest")
                            .setMessage("当前版本：$current\n\n更新说明：\n$notes\n\n下载并安装更新？")
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("下载更新") { _, _ -> downloadUpdate(apkUrl) }
                            .show()
                    } else if (showUpToDate) {
                        Toast.makeText(this, if (apkUrl.isBlank()) "暂未找到可用更新包" else "当前已是最新版本（$current）", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (showUpToDate && !isFinishing && !isDestroyed) Toast.makeText(this, "检查更新失败，请检查网络后重试", Toast.LENGTH_LONG).show()
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun compareVersions(a: String, b: String): Int {
        val x = a.split(".")
        val y = b.split(".")
        for (i in 0 until maxOf(x.size, y.size)) {
            val xi = x.getOrNull(i)?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            val yi = y.getOrNull(i)?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            if (xi != yi) return xi.compareTo(yi)
        }
        return 0
    }

    private fun downloadUpdate(url: String) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("需要允许安装应用")
                .setMessage("请允许 FamilyDocs 安装应用，授权后返回并再次检查更新。")
                .setNegativeButton("取消", null)
                .setPositiveButton("打开设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }.show()
            return
        }
        Toast.makeText(this, "正在下载更新包…", Toast.LENGTH_SHORT).show()
        Thread {
            var file: File? = null
            try {
                val dir = File(filesDir, "updates").apply { mkdirs() }
                file = File(dir, "FamilyDocs-update.apk").also { if (it.exists()) it.delete() }
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "FamilyDocs-Android")
                }
                try {
                    if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                    conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                } finally { conn.disconnect() }
                if (!file.exists() || file.length() < 100000) throw IllegalStateException("APK 文件不完整")
                runOnUiThread {
                    try {
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file!!)
                        val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                            data = uri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        }
                        startActivity(install)
                    } catch (_: Exception) {
                        Toast.makeText(this, "无法打开系统安装器，请检查安装未知应用权限", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                file?.delete()
                runOnUiThread { Toast.makeText(this, "更新下载失败，请检查网络后重试", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}

@Composable
private fun FamilyHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("familydocs", Context.MODE_PRIVATE) }
    var members by remember { mutableStateOf(readMembers(prefs)) }
    var selected by remember { mutableStateOf(members.firstOrNull() ?: "Juliet") }
    var categories by remember { mutableStateOf(readCategories(prefs)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var importCategory by remember { mutableStateOf(categories.first()) }
    var docs by remember { mutableStateOf(readDocs(prefs)) }
    var addMember by remember { mutableStateOf(false) }
    var addFolder by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var memberText by remember { mutableStateOf("") }
    var folderText by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = uris.mapNotNull { importFile(context, it, importCategory, selected) }
        val updatedDocs = docs + added
        docs = updatedDocs
        writeDocs(prefs, updatedDocs)
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Surface(color = HeaderBg, shape = RoundedCornerShape(42.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(76.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dashboard, "Home", Modifier.size(28.dp).clickable { scope.launch { scroll.animateScrollTo(0) } }, tint = Color.White)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Family Documents", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(docs.size.toString() + " Documents", color = Color.LightGray, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.Settings, "Settings", Modifier.size(28.dp).clickable { showSettings = true }, tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                members.forEachIndexed { index, member ->
                    val selectedScale by animateFloatAsState(if (member == selected) 1f else .94f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "member")
                    Column(Modifier.scale(selectedScale).clip(RoundedCornerShape(34.dp)).background(if (member == selected) Color.Black else Color.Transparent).clickable { selected = member }.padding(horizontal = 7.dp, vertical = if (member == selected) 9.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = listOf(Color(0xFFE8D9C6), Color(0xFFCAD8D9), Color(0xFFD9D1E8), Color(0xFFE9D3CC))[index % 4], shape = CircleShape, modifier = Modifier.size(50.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(member.firstOrNull()?.uppercase() ?: "•", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(member, color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
                Column(Modifier.width(60.dp).clickable { memberText = ""; addMember = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = Color.Transparent, shape = CircleShape, border = BorderStroke(1.5.dp, Color.LightGray), modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Add member", tint = Color.White, modifier = Modifier.size(26.dp)) }
                    }
                    Text("Add a Member", color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(selected + "'s Documents", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = Color.Black, shape = FolderTail, border = BorderStroke(1.4.dp, Color.White), modifier = Modifier.width(218.dp).height(56.dp).clickable { folderText = ""; addFolder = true }) {
                    Row(Modifier.padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("Add New Folder", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.FolderOpen, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(bottom = 26.dp)) {
                categories.forEachIndexed { index, category ->
                    DocumentWaveCard(
                        category, docs.count { it.owner == selected && matchesCategory(it.category, category) },
                        Pastels[index % Pastels.size], iconFor(index), expanded == category,
                        docs.filter { it.owner == selected && matchesCategory(it.category, category) },
                        onClick = { expanded = if (expanded == category) null else category; importCategory = category },
                        onImport = { importCategory = category; importer.launch(arrayOf("image/*", "application/pdf")) },
                        onDelete = { doc -> File(doc.path).delete(); docs = docs.filterNot { it == doc }; writeDocs(prefs, docs) },
                        index = index
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        Surface(color = Color.Black, shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 22.dp).size(68.dp).clickable {
            importCategory = expanded ?: categories.first()
            importer.launch(arrayOf("image/*", "application/pdf"))
        }) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.NoteAdd, "Add document", tint = Color.White, modifier = Modifier.size(34.dp)) }
        }
    }

    if (addMember) AlertDialog(onDismissRequest = { addMember = false }, title = { Text("Add a Member") },
        text = { OutlinedTextField(memberText, { memberText = it }, label = { Text("Member name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { val n = memberText.trim(); if (n.isNotEmpty() && n !in members) { members = members + n; prefs.edit().putString("members", JSONArray(members).toString()).apply(); selected = n }; addMember = false }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { addMember = false }) { Text("Cancel") } })
    if (addFolder) AlertDialog(onDismissRequest = { addFolder = false }, title = { Text("Add New Folder") },
        text = { OutlinedTextField(folderText, { folderText = it }, label = { Text("Folder name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { val n = folderText.trim(); if (n.isNotEmpty() && n !in categories) { categories = categories + n; prefs.edit().putString("categories", JSONArray(categories).toString()).apply(); expanded = n }; addFolder = false }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { addFolder = false }) { Text("Cancel") } })
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Documents stay on this device and are not uploaded to the cloud.")
            Text("App version: ${BuildConfig.VERSION_NAME}", color = Color.Gray)
            OutlinedButton(onClick = { (context as? MainActivity)?.checkForUpdates(showUpToDate = true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SystemUpdate, null)
                Spacer(Modifier.width(8.dp))
                Text("Check for updates")
            }
        } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } }
    )
}

@Composable
private fun DocumentWaveCard(
    name: String, count: Int, tint: Color, icon: ImageVector, expanded: Boolean,
    documents: List<Doc>, onClick: () -> Unit, onImport: () -> Unit, onDelete: (Doc) -> Unit, index: Int
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .985f else if (expanded) 1f else .995f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 360f),
        label = "wallet-card-scale"
    )
    val cardShape = RoundedCornerShape(26.dp)
    val gradient = when (index % 5) {
        0 -> listOf(Color(0xFFE0F8FC), Color(0xFF9EDDE9), Color(0xFFB8EAF0))
        1 -> listOf(Color(0xFFE0FFF4), Color(0xFF9CE6D1), Color(0xFFB5F0DF))
        2 -> listOf(Color(0xFFFFF1C9), Color(0xFFFFDFA0), Color(0xFFFFEAC0))
        3 -> listOf(Color(0xFFF7E4F0), Color(0xFFE8C3DD), Color(0xFFF1D8E8))
        else -> listOf(Color(0xFFF8F7F1), Color(0xFFE5E3DA), Color(0xFFF2F0E7))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = if (index == 0) 0.dp else (-28).dp)
            .zIndex(index.toFloat())
            .scale(scale)
            .padding(horizontal = 1.dp)
            .clip(cardShape)
            .background(Brush.linearGradient(gradient))
            .animateContentSize(spring(dampingRatio = .78f, stiffness = 260f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        // Subtle decorative sheen makes each category feel like a physical wallet card.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 14.dp)
                .size(width = 126.dp, height = 76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = .13f))
        )
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 22.dp, bottom = 18.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.White.copy(alpha = .9f),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Ink, modifier = Modifier.size(25.dp))
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("$count Documents", color = Ink.copy(alpha = .78f), fontSize = 12.sp)
                }
                if (index == 0 || name == "Personal Documents") {
                    Surface(color = Color.White.copy(alpha = .88f), shape = CircleShape, modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Lock, "Stored locally", tint = Ink, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 260f)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = .9f, stiffness = 300f)) + fadeOut()
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 60.dp, top = 10.dp, bottom = 4.dp)) {
                    if (documents.isEmpty()) {
                        Text("No cards yet", color = Ink.copy(alpha = .72f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    documents.forEach { doc ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CreditCard, null, tint = Ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(doc.name, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onDelete(doc) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) {
                                Text("Delete", color = Ink, fontSize = 11.sp)
                            }
                        }
                    }
                    Button(
                        onClick = onImport,
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Add Card / Document", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private class StackWave(private val variant: Int) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val w = size.width
        val h = size.height
        val offset = (variant % 3) * 4f
        val path = Path().apply {
            moveTo(0f, 30f + offset)
            cubicTo(w * .11f, 5f + offset, w * .22f, 0f, w * .34f, 15f)
            cubicTo(w * .44f, 28f, w * .49f, 39f + offset, w * .61f, 42f + offset)
            cubicTo(w * .76f, 45f, w * .86f, 31f, w, 45f + offset)
            lineTo(w, h); lineTo(0f, h); close()
        }
        return Outline.Generic(path)
    }
}

private val FolderTail: Shape = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(38f, 2f); lineTo(w - 28f, 2f)
            quadraticTo(w - 2f, 2f, w - 2f, h / 2f)
            quadraticTo(w - 2f, h - 2f, w - 28f, h - 2f)
            lineTo(38f, h - 2f); quadraticTo(19f, h - 2f, 2f, h / 2f)
            quadraticTo(19f, 2f, 38f, 2f); close()
        }
        return Outline.Generic(path)
    }
}

private fun iconFor(index: Int): ImageVector = when (index % 5) {
    0 -> Icons.Filled.MedicalServices
    1 -> Icons.Filled.School
    2 -> Icons.Filled.DirectionsCar
    3 -> Icons.Filled.PersonOutline
    else -> Icons.Filled.Description
}
private fun matchesCategory(stored: String, display: String): Boolean = stored == display || when (display) {
    "Personal Documents" -> stored == "身份证件" || stored == "银行卡"
    "Other Documents" -> stored == "家庭资料" || stored == "其他证件"
    else -> false
}
private fun readMembers(prefs: android.content.SharedPreferences): List<String> {
    return try {
        val raw = prefs.getString("members", null) ?: return listOf("Juliet", "Kevin", "Martha", "James")
        val a = JSONArray(raw)
        (0 until a.length()).map { a.getString(it) }.ifEmpty { listOf("Juliet", "Kevin", "Martha", "James") }
    } catch (_: Exception) {
        listOf("Juliet", "Kevin", "Martha", "James")
    }
}
private fun readCategories(prefs: android.content.SharedPreferences): List<String> {
    val defaults = listOf("Medical Documents", "Educational Documents", "Vehicle Documents", "Personal Documents", "Other Documents")
    return try {
        val raw = prefs.getString("categories", null) ?: return defaults
        val a = JSONArray(raw)
        (0 until a.length()).map { a.getString(it) }.ifEmpty { defaults }
    } catch (_: Exception) {
        defaults
    }
}

private fun importFile(context: Context, uri: Uri, category: String, owner: String): Doc? {
    return try {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && i >= 0) cursor.getString(i) else null
        } ?: "document"
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val target = File(dir, System.currentTimeMillis().toString() + "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
        Doc(name, target.absolutePath, category, owner)
    } catch (_: Exception) {
        null
    }
}
private fun readDocs(prefs: android.content.SharedPreferences): List<Doc> = try {
    val a = JSONArray(prefs.getString("docs", "[]"))
    (0 until a.length()).map { a.getJSONObject(it).let { o -> Doc(o.getString("name"), o.getString("path"), o.getString("category"), o.getString("owner")) } }.filter { File(it.path).exists() }
} catch (_: Exception) { emptyList() }
private fun writeDocs(prefs: android.content.SharedPreferences, docs: List<Doc>) {
    val a = JSONArray()
    docs.forEach { a.put(JSONObject().put("name", it.name).put("path", it.path).put("category", it.category).put("owner", it.owner)) }
    prefs.edit().putString("docs", a.toString()).apply()
}
