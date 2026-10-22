package com.octopus.familydocs

import android.content.Context
import android.content.ClipDescription
import android.app.Activity
import android.content.ContentValues
import android.Manifest
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.graphics.BitmapFactory
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.absoluteValue

private val ScreenBg = Color(0xFF202122)
private val HeaderBg = Color(0xFF3A3B3D)
private val Ink = Color(0xFF24292A)
private val Pastels = listOf(Color(0xFF9ADDE7), Color(0xFF9FE8D3), Color(0xFFFFE5A5), Color(0xFFEBC9E0), Color(0xFFF2F0E7))
private data class Doc(val name: String, val path: String, val category: String, val owner: String, val side: String = "front")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(32, 33, 34)
        window.navigationBarColor = android.graphics.Color.rgb(32, 33, 34)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = ScreenBg, surface = HeaderBg)) { FamilyHome() } }
        checkForUpdates(showUpToDate = false)
    }

    fun checkForUpdates(showUpToDate: Boolean) {
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
                val targetFile = File(dir, "FamilyDocs-update.apk").also { if (it.exists()) it.delete() }
                file = targetFile
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "FamilyDocs-Android")
                }
                try {
                    if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                    conn.inputStream.use { input -> targetFile.outputStream().use { output -> input.copyTo(output) } }
                } finally { conn.disconnect() }
                if (!targetFile.exists() || targetFile.length() < 100000) throw IllegalStateException("APK 文件不完整")
                runOnUiThread {
                    try {
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", targetFile)
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
    var categoryOwners by remember { mutableStateOf(runCatching {
        val json = JSONObject(prefs.getString("category_owners", "{}") ?: "{}")
        buildMap { val keys = json.keys(); while (keys.hasNext()) { val key = keys.next(); put(key, json.optString(key)) } }
    }.getOrDefault(emptyMap())) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var importCategory by remember { mutableStateOf(categories.first()) }
    var importSide by remember { mutableStateOf("front") }
    var replaceImportedSide by remember { mutableStateOf(false) }
    var docs by remember { mutableStateOf(readDocs(prefs)) }
    var addMember by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var memberText by remember { mutableStateOf("") }
    var editingMember by remember { mutableStateOf<String?>(null) }
    var editingMemberName by remember { mutableStateOf("") }
    var editingMemberNameActive by remember { mutableStateOf(false) }
    var avatarRefresh by remember { mutableIntStateOf(0) }
    var pendingAvatarMember by remember { mutableStateOf<String?>(null) }
    var confirmDeleteMember by remember { mutableStateOf<String?>(null) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var confirmDeleteCategory by remember { mutableStateOf<String?>(null) }
    var pendingGalleryExport by remember { mutableStateOf<File?>(null) }
    var flipResetToken by remember { mutableIntStateOf(0) }
    val galleryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val file = pendingGalleryExport
        if (granted && file != null) Toast.makeText(context, if (exportImageToGallery(context, file)) "已导出到相册" else "导出失败", Toast.LENGTH_LONG).show()
        else if (!granted) Toast.makeText(context, "需要照片存储权限才能导出", Toast.LENGTH_LONG).show()
        pendingGalleryExport = null
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val member = pendingAvatarMember
        if (uri != null && member != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val dir = File(context.filesDir, "member-avatars").apply { mkdirs() }
                    val target = File(dir, member.hashCode().toString() + "_avatar.jpg")
                    target.outputStream().use { output -> input.copyTo(output) }
                    prefs.edit().putString("member_avatar_" + member, target.absolutePath).apply()
                    avatarRefresh++
                }
            }
        }
        pendingAvatarMember = null
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val oldSide = if (replaceImportedSide) docs.filter { it.owner == selected && matchesCategory(it.category, importCategory) && it.side == importSide } else emptyList()
        oldSide.forEach { File(it.path).delete() }
        val added = uris.mapNotNull { importFile(context, it, importCategory, selected, importSide) }
        importSide = "front"
        replaceImportedSide = false
        val updatedDocs = docs.filterNot { it in oldSide } + added
        docs = updatedDocs
        writeDocs(prefs, updatedDocs)
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Surface(color = HeaderBg, shape = RoundedCornerShape(42.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(76.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.Black, shape = CircleShape, modifier = Modifier.size(42.dp).clickable {
                            flipResetToken++
                            val newCategory = "card_" + System.currentTimeMillis()
                            categories = categories + newCategory
                            categoryOwners = categoryOwners + (newCategory to selected)
                            prefs.edit().putString("categories", JSONArray(categories).toString()).putString("category_owners", JSONObject(categoryOwners).toString()).apply()
                            expanded = newCategory
                        }) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "新增证件卡片", tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("家庭证件夹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("共 " + docs.size + " 张证件", color = Color.LightGray, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.Settings, "设置", Modifier.size(28.dp).clickable { flipResetToken++; showSettings = true }, tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                members.forEachIndexed { index, member ->
                    val selectedScale by animateFloatAsState(if (member == selected) 1f else .94f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "member")
                    Column(Modifier.scale(selectedScale).clip(RoundedCornerShape(34.dp)).background(if (member == selected) Color.Black else Color.Transparent)
                        .combinedClickable(onClick = { flipResetToken++; selected = member }, onLongClick = { flipResetToken++; editingMember = member; editingMemberName = member; editingMemberNameActive = false })
                        .padding(horizontal = 7.dp, vertical = if (member == selected) 9.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val avatarPath = prefs.getString("member_avatar_" + member, null)
                        val avatarBitmap = remember(member, avatarPath, avatarRefresh) { avatarPath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() } }
                        Surface(color = listOf(Color(0xFFE8D9C6), Color(0xFFCAD8D9), Color(0xFFD9D1E8), Color(0xFFE9D3CC))[index % 4], shape = CircleShape, modifier = Modifier.size(50.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (avatarBitmap != null) Image(avatarBitmap, contentDescription = "$member 头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Text(member.firstOrNull()?.uppercase() ?: "•", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(member, color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
                Surface(color = Color.Transparent, shape = CircleShape, border = BorderStroke(1.5.dp, Color.LightGray), modifier = Modifier.padding(top = 9.dp).size(52.dp).clickable { memberText = ""; addMember = true }) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "添加成员", tint = Color.White, modifier = Modifier.size(26.dp)) }
                }
            }
            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 26.dp)
            ) {
                val cardHeight = maxWidth / 1.586f
                val stackStep = 68.dp
                val stackStepPx = with(LocalDensity.current) { stackStep.toPx() }
                val defaultCategories = setOf("Medical Documents", "Educational Documents", "Vehicle Documents", "Personal Documents", "Other Documents", "身份证件", "银行卡", "家庭资料", "其他证件")
                val visibleCategories = categories.filter { category ->
                    docs.any { it.owner == selected && matchesCategory(it.category, category) } ||
                        (category !in defaultCategories && categoryOwners[category] == selected)
                }
                // Keep the category order stable; selection changes the stack geometry, not the list order.
                val ordered = visibleCategories.reversed()
                val selectedIndexForHeight = ordered.indexOf(expanded).takeIf { expanded != null && it >= 0 }
                val stackHeight = if (selectedIndexForHeight == null) cardHeight + stackStep * (ordered.size - 1)
                    else cardHeight + stackStep * (ordered.lastIndex - selectedIndexForHeight).coerceAtLeast(0)
                Box(Modifier.fillMaxWidth().height(if (ordered.isEmpty()) 120.dp else stackHeight).animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))) {
                    if (ordered.isEmpty()) { }
                    ordered.forEachIndexed { index, category ->
                        val categoryDocs = docs.filter { it.owner == selected && matchesCategory(it.category, category) }
                        var dragDistance by remember(category) { mutableFloatStateOf(0f) }
                        var liveDragOffset by remember(category) { mutableFloatStateOf(0f) }
                        var dragTilt by remember(category) { mutableFloatStateOf(0f) }
                        val selectedIndex = ordered.indexOf(expanded).takeIf { expanded != null && it >= 0 }
                        val targetOffset = when {
                            selectedIndex == null -> stackStep * index
                            index == selectedIndex -> 0.dp
                            index < selectedIndex -> -cardHeight + stackStep * (index - selectedIndex)
                            else -> cardHeight + 16.dp + stackStep * (index - selectedIndex - 1)
                        }
                        val animatedOffset by animateDpAsState(targetOffset, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "card-stack-position")
                        val animatedDragOffset by animateFloatAsState(liveDragOffset, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "card-drag-settle")
                        val animatedDragTilt by animateFloatAsState(dragTilt, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "card-drag-tilt")
                        WalletCategoryCard(
                            name = category,
                            count = categoryDocs.size,
                            icon = iconFor(categories.indexOf(category).coerceAtLeast(0)),
                            documents = categoryDocs,
                            height = cardHeight,
                            isExpanded = expanded == category,
                            onImportBack = { flipResetToken++; importCategory = category; importSide = "back"; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onDropUris = { uris ->
                                val added = uris.mapNotNull { importFile(context, it, category, selected, "front") }
                                if (added.isNotEmpty()) { docs = docs + added; writeDocs(prefs, docs) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
                                .offset(y = animatedOffset)
                                .graphicsLayer {
                                    translationY = animatedDragOffset
                                    rotationZ = animatedDragTilt
                                    val lifting = if (liveDragOffset != 0f) 1.015f else 1f
                                    scaleX = lifting
                                    scaleY = lifting
                                }
                                .zIndex(if (liveDragOffset != 0f || expanded == category) ordered.size + 1f else index.toFloat()),
                            resetFlipToken = flipResetToken,
                            onClick = { flipResetToken++; expanded = if (expanded == category) null else category; importCategory = category },
                            onLongClick = { flipResetToken++; editingCategory = category },
                            onImport = { flipResetToken++; importCategory = category; importSide = "front"; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onDelete = { doc -> File(doc.path).delete(); docs = docs.filterNot { it == doc }; writeDocs(prefs, docs) }
                        )
                    }
                }
            }
        }
    }

    if (editingMember != null) {
        val member = editingMember!!
        var holdDeleteStarted by remember(member) { mutableStateOf(false) }
        var photoScale by remember(member) { mutableFloatStateOf(1f) }
        var photoOffsetX by remember(member) { mutableFloatStateOf(0f) }
        var photoOffsetY by remember(member) { mutableFloatStateOf(0f) }
        val deleteProgress by animateFloatAsState(
            targetValue = if (holdDeleteStarted) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 2000),
            label = "member-delete-countdown"
        )
        LaunchedEffect(member, holdDeleteStarted) {
            if (holdDeleteStarted) {
                delay(2000)
                if (editingMember == member) {
                    members = members.filterNot { it == member }
                    prefs.edit().putString("members", JSONArray(members).toString()).remove("member_avatar_" + member).apply()
                    if (selected == member) selected = members.firstOrNull() ?: ""
                    editingMember = null
                    editingMemberNameActive = false
                }
            }
        }
        var dialogEntered by remember(member) { mutableStateOf(false) }
        val dialogScale by animateFloatAsState(
            targetValue = if (dialogEntered) 1f else .72f,
            animationSpec = spring(dampingRatio = .42f, stiffness = 360f),
            label = "member-capsule-jelly"
        )
        val avatarPath = prefs.getString("member_avatar_" + member, null)
        val avatarBitmap = remember(member, avatarPath, avatarRefresh) {
            avatarPath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
        }
        LaunchedEffect(member) { dialogEntered = true }
        Dialog(onDismissRequest = { editingMember = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = dialogScale
                    scaleY = dialogScale
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(.62f)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.Black)
                        .padding(horizontal = 20.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Surface(
                        color = listOf(Color(0xFFE8D9C6), Color(0xFFCAD8D9), Color(0xFFD9D1E8), Color(0xFFE9D3CC))[members.indexOf(member).coerceAtLeast(0) % 4],
                        shape = CircleShape,
                        modifier = Modifier.size(124.dp).clickable {
                            pendingAvatarMember = member
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (avatarBitmap != null) {
                                Box(
                                    Modifier.fillMaxSize().clip(CircleShape).pointerInput(member) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            photoScale = (photoScale * zoom).coerceIn(1f, 5f)
                                            photoOffsetX += pan.x
                                            photoOffsetY += pan.y
                                        }
                                    }
                                ) {
                                    Image(
                                        avatarBitmap,
                                        contentDescription = "调整头像照片位置和缩放",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = photoScale
                                            scaleY = photoScale
                                            translationX = photoOffsetX
                                            translationY = photoOffsetY
                                        }
                                    )
                                }
                            } else {
                                Text(member.firstOrNull()?.uppercase() ?: "•", color = Ink, fontSize = 54.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (editingMemberNameActive) {
                        OutlinedTextField(
                            value = editingMemberName,
                            onValueChange = { editingMemberName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp)
                        )
                    } else {
                        Text(
                            editingMemberName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { editingMemberNameActive = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = HeaderBg,
                        shape = CircleShape,
                        modifier = Modifier.size(58.dp).drawBehind {
                            if (deleteProgress > 0f) {
                                drawArc(
                                    color = Color(0xFFFF8A80),
                                    startAngle = -90f,
                                    sweepAngle = deleteProgress * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!holdDeleteStarted) {
                                    editingMember = null
                                    editingMemberNameActive = false
                                }
                            },
                            onLongClick = { holdDeleteStarted = true }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "删除成员", tint = Color(0xFFFF8A80), modifier = Modifier.size(28.dp))
                        }
                    }
                    Surface(
                        color = Color(0xFFB2F0DF),
                        shape = CircleShape,
                        modifier = Modifier.size(58.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                            val newName = editingMemberName.trim()
                            if (newName.isNotEmpty() && (newName == member || newName !in members)) {
                                if (newName != member) {
                                    members = members.map { if (it == member) newName else it }
                                    prefs.edit().putString("members", JSONArray(members).toString()).apply()
                                    val avatar = prefs.getString("member_avatar_" + member, null)
                                    if (avatar != null) prefs.edit().putString("member_avatar_" + newName, avatar).remove("member_avatar_" + member).apply()
                                    docs = docs.map { if (it.owner == member) it.copy(owner = newName) else it }
                                    writeDocs(prefs, docs)
                                    categoryOwners = categoryOwners.mapValues { (_, owner) -> if (owner == member) newName else owner }
                                    prefs.edit().putString("category_owners", JSONObject(categoryOwners).toString()).apply()
                                    if (selected == member) selected = newName
                                }
                                editingMember = null
                                editingMemberNameActive = false
                            }
                        })
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, contentDescription = "确认", tint = Ink, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }
    if (editingCategory != null) {
        val category = editingCategory!!
        val categoryDocs = docs.filter { it.owner == selected && matchesCategory(it.category, category) }
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif")
        val frontPhoto = categoryDocs.firstOrNull { it.side == "front" && File(it.path).extension.lowercase() in imageExts }
        val backPhoto = categoryDocs.firstOrNull { it.side == "back" && File(it.path).extension.lowercase() in imageExts }
        AlertDialog(onDismissRequest = { editingCategory = null }, title = { Text("编辑证件卡片") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { flipResetToken++; importCategory = category; importSide = "front"; replaceImportedSide = true; editingCategory = null; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("正面照片") }
                    OutlinedButton(onClick = { flipResetToken++; importCategory = category; importSide = "back"; replaceImportedSide = true; editingCategory = null; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("反面照片") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { frontPhoto?.let { file -> exportOrRequestGallery(context, File(file.path), { pendingGalleryExport = it }, { galleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }) { ok -> Toast.makeText(context, if (ok) "正面照片已导出" else "导出失败", Toast.LENGTH_LONG).show() } } }, enabled = frontPhoto != null, modifier = Modifier.weight(1f)) { Text("导出正面") }
                    OutlinedButton(onClick = { backPhoto?.let { file -> exportOrRequestGallery(context, File(file.path), { pendingGalleryExport = it }, { galleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }) { ok -> Toast.makeText(context, if (ok) "反面照片已导出" else "导出失败", Toast.LENGTH_LONG).show() } } }, enabled = backPhoto != null, modifier = Modifier.weight(1f)) { Text("导出反面") }
                }
            } },
            confirmButton = { TextButton(onClick = { editingCategory = null }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { flipResetToken++; confirmDeleteCategory = category; editingCategory = null }) { Text("删除卡片", color = MaterialTheme.colorScheme.error) } }
        )
    }

    if (confirmDeleteCategory != null) {
        val category = confirmDeleteCategory!!
        AlertDialog(onDismissRequest = { confirmDeleteCategory = null }, title = { Text("删除证件卡片？") },
            confirmButton = { TextButton(onClick = {
                flipResetToken++
                val removed = docs.filter { it.owner == selected && matchesCategory(it.category, category) }
                removed.forEach { File(it.path).delete() }
                docs = docs.filterNot { it in removed }
                val hasOtherOwners = docs.any { it.owner != selected && matchesCategory(it.category, category) }
                if (!hasOtherOwners) {
                    categories = categories.filterNot { it == category }
                    categoryOwners = categoryOwners - category
                }
                prefs.edit().putString("categories", JSONArray(categories).toString()).putString("category_owners", JSONObject(categoryOwners).toString()).apply()
                writeDocs(prefs, docs)
                if (expanded == category) expanded = null
                confirmDeleteCategory = null
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteCategory = null }) { Text("取消") } })
    }
    if (confirmDeleteMember != null) {
        val member = confirmDeleteMember!!
        AlertDialog(onDismissRequest = { confirmDeleteMember = null }, title = { Text("删除家庭成员？") },
            text = { Text("将从家庭成员列表中移除“" + member + "”。已导入的证件文件会保留在本机。") },
            confirmButton = { TextButton(onClick = {
                members = members.filterNot { it == member }
                prefs.edit().putString("members", JSONArray(members).toString()).remove("member_avatar_" + member).apply()
                if (selected == member) selected = members.firstOrNull() ?: ""
                confirmDeleteMember = null
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteMember = null }) { Text("取消") } })
    }
    if (addMember) AlertDialog(onDismissRequest = { addMember = false }, title = { Text("Add a Member") },
        text = { OutlinedTextField(memberText, { memberText = it }, label = { Text("成员姓名") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { val n = memberText.trim(); if (n.isNotEmpty() && n !in members) { members = members + n; prefs.edit().putString("members", JSONArray(members).toString()).apply(); selected = n }; addMember = false }) { Text("添加") } },
        dismissButton = { TextButton(onClick = { addMember = false }) { Text("取消") } })
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("所有证件仅保存在本机，不会上传到云端。")
            Text("应用版本：${BuildConfig.VERSION_NAME}", color = Color.Gray)
            OutlinedButton(onClick = { (context as? MainActivity)?.checkForUpdates(showUpToDate = true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.SystemUpdate, null)
                Spacer(Modifier.width(8.dp))
                Text("检查更新")
            }
        } },
        confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WalletCategoryCard(
    name: String,
    count: Int,
    icon: ImageVector,
    documents: List<Doc>,
    height: androidx.compose.ui.unit.Dp,
    isExpanded: Boolean,
    onImportBack: () -> Unit,
    onDropUris: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    resetFlipToken: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onImport: () -> Unit,
    onDelete: (Doc) -> Unit
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .985f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 360f),
        label = "wallet-card-scale"
    )
    val cardShape = RoundedCornerShape(20.dp)
    val currentDropHandler by rememberUpdatedState(onDropUris)
    var flipped by remember(name, resetFlipToken) { mutableStateOf(false) }
    val currentFlipped by rememberUpdatedState(flipped)
    val flipRotation by animateFloatAsState(targetValue = if (flipped) 180f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f), label = "card-flip-rotation")
    val firstImage = documents.firstOrNull { doc ->
        val ext = File(doc.path).extension.lowercase()
        (doc.side == (if (flipped) "back" else "front")) && ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif")
    }
    val bitmap = remember(firstImage?.path) {
        firstImage?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
    }
    val gradient = when (name.hashCode().absoluteValue % 5) {
        0 -> listOf(Color(0xFFE0F8FC), Color(0xFF9EDDE9), Color(0xFFB8EAF0))
        1 -> listOf(Color(0xFFE0FFF4), Color(0xFF9CE6D1), Color(0xFFB5F0DF))
        2 -> listOf(Color(0xFFFFF1C9), Color(0xFFFFDFA0), Color(0xFFFFEAC0))
        3 -> listOf(Color(0xFFF7E4F0), Color(0xFFE8C3DD), Color(0xFFF1D8E8))
        else -> listOf(Color(0xFFF8F7F1), Color(0xFFE5E3DA), Color(0xFFF2F0E7))
    }
    Box(
        modifier
            .scale(scale)
            .shadow(elevation = 18.dp, shape = cardShape, clip = false)
            .clip(cardShape)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> event.mimeTypes().any { it.startsWith("image/") } },
                target = remember {
                    object : DragAndDropTarget {
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val activity = context as? Activity
                            val permission = activity?.requestDragAndDropPermissions(event.toAndroidDragEvent())
                            return try {
                                val clip = event.toAndroidDragEvent().clipData
                                val uris = (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index).uri }
                                if (uris.isEmpty()) false else { currentDropHandler(uris); true }
                            } catch (_: Exception) { false }
                            finally { permission?.release() }
                        }
                    }
                }
            )
            .pointerInput(name, resetFlipToken) {
                var horizontalDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDistance = 0f },
                    onDragEnd = {
                        if (horizontalDistance.absoluteValue > 48f) flipped = !currentFlipped
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDistance += dragAmount
                    }
                )
            }
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationY = flipRotation; cameraDistance = 18 * density }.background(Brush.linearGradient(gradient))) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = name, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = if (flipped) 180f else 0f })
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)))
            } else {
                Icon(Icons.Filled.Add, contentDescription = if (flipped) "添加卡片背面照片" else "导入证件照片",
                    tint = Ink.copy(alpha = .72f), modifier = Modifier.align(Alignment.Center).size(92.dp)
                        .graphicsLayer { rotationY = if (flipped) 180f else 0f }.clickable { if (flipped) onImportBack() else onImport() })
            }
        }
    }
}

private fun exportOrRequestGallery(context: Context, file: File, setPending: (File) -> Unit, requestPermission: () -> Unit, done: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= 29) done(exportImageToGallery(context, file))
    else if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) done(exportImageToGallery(context, file))
    else { setPending(file); requestPermission() }
}

private fun exportImageToGallery(context: Context, source: File): Boolean {
    if (!source.exists() || source.length() == 0L) return false
    return runCatching {
        val mime = when (source.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "FamilyDocs_" + System.currentTimeMillis() + "." + source.extension.ifBlank { "jpg" })
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FamilyDocs")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: return false
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FamilyDocs").apply { mkdirs() }
            val target = File(dir, "FamilyDocs_" + System.currentTimeMillis() + "." + source.extension.ifBlank { "jpg" })
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            true
        }
    }.getOrDefault(false)
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

private fun importFile(context: Context, uri: Uri, category: String, owner: String, side: String = "front"): Doc? {
    return try {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && i >= 0) cursor.getString(i) else null
        } ?: "document"
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val target = File(dir, System.currentTimeMillis().toString() + "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
        Doc(name, target.absolutePath, category, owner, side)
    } catch (_: Exception) {
        null
    }
}
private fun readDocs(prefs: android.content.SharedPreferences): List<Doc> = try {
    val a = JSONArray(prefs.getString("docs", "[]"))
    (0 until a.length()).map { a.getJSONObject(it).let { o -> Doc(o.getString("name"), o.getString("path"), o.getString("category"), o.getString("owner"), o.optString("side", "front")) } }.filter { File(it.path).exists() }
} catch (_: Exception) { emptyList() }
private fun writeDocs(prefs: android.content.SharedPreferences, docs: List<Doc>) {
    val a = JSONArray()
    docs.forEach { a.put(JSONObject().put("name", it.name).put("path", it.path).put("category", it.category).put("owner", it.owner).put("side", it.side)) }
    prefs.edit().putString("docs", a.toString()).apply()
}
