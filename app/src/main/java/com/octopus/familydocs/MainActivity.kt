package com.octopus.familydocs

import android.content.Context
import android.content.ClipData
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
import android.widget.ImageView
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
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

private val ScreenBg = Color(0xFF262728)
private val HeaderBg = Color(0xFF3A3B3D)
private val Ink = Color(0xFF24292A)
private val Pastels = listOf(Color(0xFF9ADDE7), Color(0xFF9FE8D3), Color(0xFFFFE5A5), Color(0xFFEBC9E0), Color(0xFFF2F0E7))
private data class Doc(val name: String, val path: String, val category: String, val owner: String, val side: String = "front")

class MainActivity : ComponentActivity() {
    private val sharedImageUris = mutableStateListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(32, 33, 34)
        window.navigationBarColor = android.graphics.Color.rgb(32, 33, 34)
        restorePendingSharedImages()
        collectSharedImages(intent)
        File(filesDir, "updates/drag-test-image.jpg").delete()
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = ScreenBg, surface = HeaderBg)) { FamilyHome(sharedImageUris) { consumed -> removePendingSharedImages(consumed) } } }
        checkForUpdates(showUpToDate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        collectSharedImages(intent)
    }

    private fun restorePendingSharedImages() {
        val saved = runCatching {
            val array = org.json.JSONArray(getSharedPreferences("familydocs", Context.MODE_PRIVATE).getString("pending_shared_images", "[]"))
            (0 until array.length()).mapNotNull { index ->
                Uri.parse(array.optString(index)).takeIf { uri ->
                    uri.authority == "$packageName.fileprovider" &&
                        File(filesDir, "pending-shares/" + uri.lastPathSegment.orEmpty()).exists()
                }
            }
        }.getOrDefault(emptyList())
        sharedImageUris.addAll(saved)
    }

    private fun persistPendingSharedImages() {
        val json = org.json.JSONArray()
        sharedImageUris.forEach { json.put(it.toString()) }
        // Commit before the app can be backgrounded or replaced by an update.
        getSharedPreferences("familydocs", Context.MODE_PRIVATE).edit()
            .putString("pending_shared_images", json.toString()).commit()
    }

    private fun removePendingSharedImages(uris: List<Uri>) {
        sharedImageUris.removeAll(uris.toSet())
        uris.forEach { uri ->
            if (uri.authority == "$packageName.fileprovider") {
                runCatching { File(filesDir, "pending-shares/" + uri.lastPathSegment.orEmpty()).delete() }
            }
        }
        persistPendingSharedImages()
    }

    @Suppress("DEPRECATION")
    private fun collectSharedImages(incoming: Intent?) {
        if (incoming == null || (incoming.action != Intent.ACTION_SEND && incoming.action != Intent.ACTION_SEND_MULTIPLE)) return
        val received = linkedSetOf<Uri>()
        incoming.data?.let(received::add)
        incoming.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(received::add)
        }
        runCatching { incoming.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()?.let(received::add)
        runCatching { incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()?.let { received.addAll(it) }
        val incomingUris = received.toList()
        if (incomingUris.isEmpty()) return
        Thread {
            val dir = File(filesDir, "pending-shares").apply { mkdirs() }
            val copied = incomingUris.mapIndexedNotNull { index, uri ->
                runCatching {
                    val extension = when (contentResolver.getType(uri)?.lowercase()) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/heic" -> "heic"
                        "image/heif" -> "heif"
                        "image/gif" -> "gif"
                        else -> "jpg"
                    }
                    val target = File(dir, "${System.currentTimeMillis()}_${index}_${kotlin.random.Random.nextInt(1000, 9999)}.${extension}")
                    contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                        ?: return@mapIndexedNotNull null
                    FileProvider.getUriForFile(this, "$packageName.fileprovider", target)
                }.getOrNull()
            }
            runOnUiThread {
                sharedImageUris.addAll(copied.filter { it !in sharedImageUris })
                persistPendingSharedImages()
                if (copied.size < incomingUris.size) {
                    Toast.makeText(this, "部分图片导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FamilyHome(sharedImageUris: List<Uri> = emptyList(), onSharedImagesConsumed: (List<Uri>) -> Unit = {}) {
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
    var importCategory by remember { mutableStateOf(categories.firstOrNull() ?: "身份证件") }
    var importSide by remember { mutableStateOf("front") }
    var replaceImportedSide by remember { mutableStateOf(false) }
    var docs by remember { mutableStateOf(readDocs(prefs)) }
    val removeMemberData: (String) -> Unit = { memberToRemove ->
        val removedDocs = docs.filter { it.owner == memberToRemove }
        removedDocs.forEach { runCatching { File(it.path).delete() } }
        docs = docs.filterNot { it.owner == memberToRemove }
        writeDocs(prefs, docs)

        val avatarKey = "member_avatar_" + memberToRemove
        prefs.getString(avatarKey, null)?.let { path ->
            runCatching {
                val avatarFile = File(path).canonicalFile
                val avatarDir = File(context.filesDir, "member-avatars").canonicalFile
                if (avatarFile.parentFile == avatarDir) avatarFile.delete()
            }
        }
        val ownersJson = JSONObject(prefs.getString("category_owners", "{}") ?: "{}")
        val keys = ownersJson.keys().asSequence().toList()
        val removedCategories = keys.filter { ownersJson.optString(it) == memberToRemove }
        removedCategories.forEach { category ->
            val usedByOtherMembers = docs.any { it.category == category && it.owner != memberToRemove }
            if (!usedByOtherMembers) {
                categories = categories.filterNot { it == category }
                ownersJson.remove(category)
            } else {
                ownersJson.put(category, "")
            }
        }
        categoryOwners = buildMap {
            val remaining = ownersJson.keys()
            while (remaining.hasNext()) {
                val key = remaining.next()
                put(key, ownersJson.optString(key))
            }
        }
        prefs.edit()
            .remove(avatarKey)
            .putString("categories", JSONArray(categories).toString())
            .putString("category_owners", ownersJson.toString())
            .apply()
        if (selected == memberToRemove) selected = members.firstOrNull { it != memberToRemove } ?: ""
    }
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
    var viewingSharedImage by remember { mutableStateOf<Uri?>(null) }
    var viewingSharedImageVisible by remember { mutableStateOf(false) }
    var exitingCategories by remember { mutableStateOf(setOf<String>()) }
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
                    var removeDropTargeted by remember { mutableStateOf(false) }
                    val currentSharedImages by rememberUpdatedState(sharedImageUris)
                    val currentRemoveHandler by rememberUpdatedState(onSharedImagesConsumed)
                    Surface(
                        color = if (removeDropTargeted) Color(0xFFB94A52) else Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                            .dragAndDropTarget(
                                shouldStartDragAndDrop = { event ->
                                    runCatching {
                                        val clip = event.toAndroidDragEvent().clipData
                                        clip != null && (0 until clip.itemCount).any { clip.getItemAt(it).uri != null }
                                    }.getOrDefault(false)
                                },
                                target = remember {
                                    object : DragAndDropTarget {
                                        override fun onStarted(event: DragAndDropEvent) {}
                                        override fun onEntered(event: DragAndDropEvent) { removeDropTargeted = true }
                                        override fun onMoved(event: DragAndDropEvent) { removeDropTargeted = true }
                                        override fun onExited(event: DragAndDropEvent) { removeDropTargeted = false }
                                        override fun onEnded(event: DragAndDropEvent) { removeDropTargeted = false }
                                        override fun onDrop(event: DragAndDropEvent): Boolean {
                                            removeDropTargeted = false
                                            val clip = event.toAndroidDragEvent().clipData ?: return false
                                            val dragged = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.toSet()
                                            val queued = currentSharedImages.filter { it in dragged }
                                            if (queued.isEmpty()) return false
                                            currentRemoveHandler(queued)
                                            return true
                                        }
                                    }
                                }
                            )
                            .clickable {
                                if (removeDropTargeted) return@clickable
                                flipResetToken++
                                val newCategory = "card_" + System.currentTimeMillis()
                                categories = categories + newCategory
                                categoryOwners = categoryOwners + (newCategory to selected)
                                prefs.edit().putString("categories", JSONArray(categories).toString()).putString("category_owners", JSONObject(categoryOwners).toString()).apply()
                                expanded = newCategory
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (removeDropTargeted) Icons.Filled.Remove else Icons.Filled.Add,
                                if (removeDropTargeted) "删除待处理图片" else "新增证件卡片",
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    if (sharedImageUris.isNotEmpty()) {
                        Row(
                            Modifier.weight(1f).padding(horizontal = 10.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sharedImageUris.forEach { sharedUri ->
                                key(sharedUri) {
                                    val preview by produceState<Bitmap?>(initialValue = null, sharedUri) {
                                        value = withContext(Dispatchers.IO) { decodeUriBitmap(context, sharedUri, 256) }
                                    }
                                    val previewBitmap = preview
                                    if (previewBitmap != null) {
                                        AndroidView(
                                            factory = { viewContext ->
                                                ImageView(viewContext).apply {
                                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                                    background = android.graphics.drawable.GradientDrawable().apply {
                                                        shape = android.graphics.drawable.GradientDrawable.OVAL
                                                        setColor(android.graphics.Color.DKGRAY)
                                                    }
                                                    clipToOutline = true
                                                    setImageBitmap(previewBitmap)
                                                    val doubleTapDetector = GestureDetector(viewContext, object : GestureDetector.SimpleOnGestureListener() {
                                                        override fun onDoubleTap(event: MotionEvent): Boolean {
                                                            viewingSharedImage = sharedUri
                                                            viewingSharedImageVisible = true
                                                            return true
                                                        }
                                                    })
                                                    setOnTouchListener { _, event ->
                                                        doubleTapDetector.onTouchEvent(event)
                                                        false
                                                    }
                                                    setOnDragListener { draggedView, event ->
                                                        if (event.action == android.view.DragEvent.ACTION_DRAG_ENDED) {
                                                            draggedView.animate().scaleX(1f).scaleY(1f).alpha(1f).translationZ(0f).setDuration(160L).start()
                                                        }
                                                        false
                                                    }
                                                    setOnLongClickListener { view ->
                                                        runCatching {
                                                            view.animate().scaleX(1.12f).scaleY(1.12f).alpha(0.78f).translationZ(12f).setDuration(130L).start()
                                                            val clip = ClipData.newUri(view.context.contentResolver, "FamilyDocs 图片", sharedUri)
                                                            view.startDragAndDrop(clip, View.DragShadowBuilder(view), null, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
                                                        }.getOrDefault(false)
                                                    }
                                                }
                                            },
                                            update = { it.setImageBitmap(previewBitmap) },
                                            modifier = Modifier.size(42.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.72f), CircleShape)

                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Icon(Icons.Filled.Settings, "设置", Modifier.size(28.dp).clickable { flipResetToken++; showSettings = true }, tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                members.forEachIndexed { index, member ->
                    val selectedScale by animateFloatAsState(if (member == selected) 1f else .94f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "member")
                    var memberDragX by remember(member) { mutableFloatStateOf(0f) }
                    val memberPillColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (member == selected) Color.Black else Color.Transparent,
                        animationSpec = tween(190),
                        label = "member-selection-color"
                    )
                    val selectedVerticalPadding by animateDpAsState(
                        targetValue = if (member == selected) 9.dp else 6.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "member-selection-padding"
                    )
                    Column(
                        Modifier.scale(selectedScale)
                            .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                            .zIndex(if (memberDragX != 0f) 2f else 0f)
                            .graphicsLayer { translationX = memberDragX }
                            .clip(RoundedCornerShape(34.dp))
                            .background(memberPillColor)
                            .pointerInput(member, members) {
                                detectTapGestures(
                                    onTap = { flipResetToken++; selected = member },
                                    onDoubleTap = {
                                        flipResetToken++
                                        editingMember = member
                                        editingMemberName = member
                                        editingMemberNameActive = false
                                    }
                                )
                            }
                            .pointerInput(member, members) {
                                var accumulated = 0f
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { accumulated = 0f },
                                    onDragEnd = { memberDragX = 0f; accumulated = 0f },
                                    onDragCancel = { memberDragX = 0f; accumulated = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        memberDragX += dragAmount.x
                                        accumulated += dragAmount.x
                                        val step = 64.dp.toPx()
                                        if (accumulated > step && index < members.lastIndex) {
                                            val reordered = members.toMutableList()
                                            val item = reordered.removeAt(index)
                                            reordered.add(index + 1, item)
                                            members = reordered
                                            prefs.edit().putString("members", JSONArray(members).toString()).apply()
                                            accumulated -= step
                                        } else if (accumulated < -step && index > 0) {
                                            val reordered = members.toMutableList()
                                            val item = reordered.removeAt(index)
                                            reordered.add(index - 1, item)
                                            members = reordered
                                            prefs.edit().putString("members", JSONArray(members).toString()).apply()
                                            accumulated += step
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 7.dp, vertical = selectedVerticalPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val avatarPath = prefs.getString("member_avatar_" + member, null)
                        val avatarBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, member, avatarPath, avatarRefresh) {
                            value = avatarPath?.let { path ->
                                withContext(Dispatchers.IO) { decodeImageBitmap(path, 256) }
                            }
                        }
                        val loadedAvatarBitmap = avatarBitmap
                        val savedAvatarScale = prefs.getFloat("member_avatar_scale_" + member, 1f)
                        val savedAvatarX = prefs.getFloat("member_avatar_x_" + member, 0f)
                        val savedAvatarY = prefs.getFloat("member_avatar_y_" + member, 0f)
                        Surface(color = listOf(Color(0xFFE8D9C6), Color(0xFFCAD8D9), Color(0xFFD9D1E8), Color(0xFFE9D3CC))[index % 4], shape = CircleShape, modifier = Modifier.size(50.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (loadedAvatarBitmap != null) {
                                    Image(
                                        loadedAvatarBitmap,
                                        contentDescription = "$member 头像",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = savedAvatarScale
                                            scaleY = savedAvatarScale
                                            translationX = savedAvatarX * size.width
                                            translationY = savedAvatarY * size.height
                                        }
                                    )
                                } else Text(member.firstOrNull()?.uppercase() ?: "•", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
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
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // Alpha-mask the lower card stack to true transparency, avoiding a visible overlay edge.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White,
                                    0.58f to Color.White,
                                    0.82f to Color.White.copy(alpha = 0.42f),
                                    1.0f to Color.Transparent
                                ),
                                startY = size.height - 190.dp.toPx(),
                                endY = size.height
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    .verticalScroll(scroll, enabled = expanded == null)
                    .then(if (expanded != null) Modifier.pointerInput(expanded) { var verticalDistance = 0f; detectVerticalDragGestures(onDragStart = { verticalDistance = 0f }, onDragEnd = { if (verticalDistance.absoluteValue > 42f) { expanded = null; flipResetToken++ } }, onVerticalDrag = { change, amount -> change.consume(); verticalDistance += amount }) } else Modifier)
                    .padding(bottom = 26.dp)
            ) {
                val cardHeight = maxWidth / 1.586f
                val stackStep = 68.dp
                val stackStepPx = with(LocalDensity.current) { stackStep.toPx() }
                val defaultCategories = setOf("Medical Documents", "Educational Documents", "Vehicle Documents", "Personal Documents", "Other Documents", "身份证件", "银行卡", "家庭资料", "其他证件")
                val visibleCategories = categories.filter { category ->
                    category in exitingCategories ||
                        docs.any { it.owner == selected && matchesCategory(it.category, category) } ||
                        (category !in defaultCategories && categoryOwners[category] == selected)
                }
                // Cards are displayed in reverse order; drag reordering persists the visual order.
                val ordered = visibleCategories.reversed()
                val reorderCard: (String, Int) -> Unit = { draggedCategory, direction ->
                    val visualOrder = categories.filter { it in visibleCategories }.reversed().toMutableList()
                    val from = visualOrder.indexOf(draggedCategory)
                    val to = (from + direction).coerceIn(0, visualOrder.lastIndex)
                    if (from >= 0 && to != from) {
                        val moved = visualOrder.removeAt(from)
                        visualOrder.add(to, moved)
                        val categoryIterator = visualOrder.reversed().iterator()
                        categories = categories.map { item -> if (item in visibleCategories) categoryIterator.next() else item }
                        prefs.edit().putString("categories", JSONArray(categories).toString()).commit()
                    }
                }
                val selectedIndexForHeight = ordered.indexOf(expanded).takeIf { expanded != null && it >= 0 }
                val stackHeight = if (selectedIndexForHeight == null) cardHeight + stackStep * (ordered.size - 1)
                    else cardHeight + stackStep * (ordered.lastIndex - selectedIndexForHeight).coerceAtLeast(0)
                Box(Modifier.fillMaxWidth().height(if (ordered.isEmpty()) 120.dp else stackHeight).animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))) {
                    if (ordered.isEmpty()) { }
                    ordered.forEachIndexed { index, category -> key(category) {
                        val categoryDocs = docs.filter { it.owner == selected && matchesCategory(it.category, category) }
                        val selectedIndex = ordered.indexOf(expanded).takeIf { expanded != null && it >= 0 }
                        val targetOffset = when {
                            selectedIndex == null -> stackStep * index
                            index == selectedIndex -> 0.dp
                            index < selectedIndex -> -cardHeight + stackStep * (index - selectedIndex)
                            else -> cardHeight + 16.dp + stackStep * (index - selectedIndex - 1)
                        }
                        val animatedOffset by animateDpAsState(targetOffset, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "card-stack-position")
                        AnimatedVisibility(
                            visible = category !in exitingCategories,
                            enter = fadeIn(tween(220)) + expandVertically(animationSpec = tween(240)),
                            exit = fadeOut(tween(180)) + shrinkVertically(animationSpec = tween(180)),
                            modifier = Modifier.fillMaxWidth().height(cardHeight).offset(y = animatedOffset)
                                .zIndex(if (expanded == category) ordered.size + 2f else (ordered.size - index).toFloat())
                        ) {
                        WalletCategoryCard(
                            name = category,
                            count = categoryDocs.size,
                            icon = iconFor(categories.indexOf(category).coerceAtLeast(0)),
                            documents = categoryDocs,
                            height = cardHeight,
                            isExpanded = expanded == category,
                            onImportBack = { flipResetToken++; importCategory = category; importSide = "back"; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onDropUris = { uris, side ->
                                val oldSide = docs.filter { it.owner == selected && matchesCategory(it.category, category) && it.side == side }
                                val added = uris.mapNotNull { importFile(context, it, category, selected, side) }
                                if (added.isNotEmpty()) {
                                    oldSide.forEach { runCatching { File(it.path).delete() } }
                                    docs = docs.filterNot { it in oldSide } + added
                                    writeDocs(prefs, docs)
                                    onSharedImagesConsumed(uris)
                                }
                            },
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                val lifting = if (expanded == category) 1.015f else 1f
                                scaleX = lifting
                                scaleY = lifting
                            },
                            resetFlipToken = flipResetToken,
                            onClick = { flipResetToken++; expanded = if (expanded == category) null else category; importCategory = category },
                            onDoubleClick = { flipResetToken++; editingCategory = category },
                            onReorder = reorderCard,
                            onImport = { flipResetToken++; importCategory = category; importSide = "front"; importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onDelete = { doc -> File(doc.path).delete(); docs = docs.filterNot { it == doc }; writeDocs(prefs, docs) }
                        )
                        }
                    } }
                }
            }
        }
    }

    if (editingMember != null) {
        val member = editingMember!!
        var holdDeleteStarted by remember(member) { mutableStateOf(false) }
        var photoScale by remember(member) { mutableFloatStateOf(prefs.getFloat("member_avatar_scale_" + member, 1f)) }
        var photoOffsetX by remember(member) { mutableFloatStateOf(prefs.getFloat("member_avatar_x_" + member, 0f)) }
        var photoOffsetY by remember(member) { mutableFloatStateOf(prefs.getFloat("member_avatar_y_" + member, 0f)) }
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
                    prefs.edit().putString("members", JSONArray(members).toString()).apply()
                    removeMemberData(member)
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
        val avatarBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, member, avatarPath, avatarRefresh) {
            value = avatarPath?.let { path ->
                withContext(Dispatchers.IO) { decodeImageBitmap(path, 512) }
            }
        }
        val loadedDialogAvatar = avatarBitmap
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
                    modifier = Modifier.width(240.dp).heightIn(min = 360.dp)
                        .clip(RoundedCornerShape(120.dp))
                        .background(Color.Black)
                        .padding(horizontal = 20.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Surface(
                        color = listOf(Color(0xFFE8D9C6), Color(0xFFCAD8D9), Color(0xFFD9D1E8), Color(0xFFE9D3CC))[members.indexOf(member).coerceAtLeast(0) % 4],
                        shape = CircleShape,
                        modifier = Modifier.size(184.dp).clickable {
                            pendingAvatarMember = member
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (loadedDialogAvatar != null) {
                                Box(
                                    Modifier.fillMaxSize().clip(CircleShape).pointerInput(member) {
                                        detectTransformGestures { centroid, pan, zoom, _ ->
                                            val viewWidth = size.width.coerceAtLeast(1).toFloat()
                                            val viewHeight = size.height.coerceAtLeast(1).toFloat()
                                            val nextScale = (photoScale * zoom).coerceIn(1f, 5f)
                                            val scaleFactor = nextScale / photoScale
                                            photoOffsetX = (photoOffsetX * scaleFactor + pan.x / viewWidth).coerceIn(-1.5f, 1.5f)
                                            photoOffsetY = (photoOffsetY * scaleFactor + pan.y / viewHeight).coerceIn(-1.5f, 1.5f)
                                            photoScale = nextScale
                                        }
                                    }
                                ) {
                                    Image(
                                        loadedDialogAvatar,
                                        contentDescription = "调整头像照片位置和缩放",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            scaleX = photoScale
                                            scaleY = photoScale
                                            translationX = photoOffsetX * size.width
                                            translationY = photoOffsetY * size.height
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
                        modifier = Modifier.size(58.dp).drawWithContent {
                            drawContent()
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
                                prefs.edit()
                                    .putFloat("member_avatar_scale_" + newName, photoScale)
                                    .putFloat("member_avatar_x_" + newName, photoOffsetX)
                                    .putFloat("member_avatar_y_" + newName, photoOffsetY)
                                    .apply()
                                if (newName != member) {
                                    prefs.edit()
                                        .putFloat("member_avatar_scale_" + newName, photoScale)
                                        .putFloat("member_avatar_x_" + newName, photoOffsetX)
                                        .putFloat("member_avatar_y_" + newName, photoOffsetY)
                                        .remove("member_avatar_scale_" + member)
                                        .remove("member_avatar_x_" + member)
                                        .remove("member_avatar_y_" + member)
                                        .apply()
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
    var viewingPhoto by remember { mutableStateOf<Doc?>(null) }
    if (editingCategory != null) {
        val category = editingCategory!!
        val categoryDocs = docs.filter { it.owner == selected && matchesCategory(it.category, category) }
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif")
        val frontPhoto = categoryDocs.firstOrNull { it.side == "front" && File(it.path).extension.lowercase() in imageExts }
        val backPhoto = categoryDocs.firstOrNull { it.side == "back" && File(it.path).extension.lowercase() in imageExts }
        AlertDialog(onDismissRequest = { editingCategory = null }, title = null,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(frontPhoto, backPhoto).forEachIndexed { index, photo ->
                            val side = if (index == 0) "front" else "back"
                            val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, photo?.path) {
                                value = photo?.let { item -> withContext(Dispatchers.IO) { decodeImageBitmap(item.path, 320) } }
                            }
                            val loadedThumbnail = thumbnail
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF38383D),
                                modifier = Modifier.weight(1f).height(104.dp).clickable {
                                    if (photo != null) viewingPhoto = photo
                                    else {
                                        flipResetToken++
                                        importCategory = category
                                        importSide = side
                                        replaceImportedSide = true
                                        editingCategory = null
                                        importer.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    }
                                }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (loadedThumbnail != null) {
                                        Image(loadedThumbnail, contentDescription = if (side == "front") "正面照片" else "反面照片",
                                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Filled.Add, if (side == "front") "添加正面照片" else "添加反面照片",
                                            tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { frontPhoto?.let { file -> exportOrRequestGallery(context, File(file.path), { pendingGalleryExport = it }, { galleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }) { ok -> Toast.makeText(context, if (ok) "正面照片已导出" else "导出失败", Toast.LENGTH_LONG).show() } } }, enabled = frontPhoto != null, modifier = Modifier.weight(1f)) { Text("导出正面") }
                        OutlinedButton(onClick = { backPhoto?.let { file -> exportOrRequestGallery(context, File(file.path), { pendingGalleryExport = it }, { galleryPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }) { ok -> Toast.makeText(context, if (ok) "反面照片已导出" else "导出失败", Toast.LENGTH_LONG).show() } } }, enabled = backPhoto != null, modifier = Modifier.weight(1f)) { Text("导出反面") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { flipResetToken++; confirmDeleteCategory = category; editingCategory = null }) { Text("删除卡片", color = MaterialTheme.colorScheme.error) } }
        )
    }

    if (viewingSharedImage != null) {
        val uri = viewingSharedImage!!
        val fullBitmap by produceState<Bitmap?>(initialValue = null, uri) {
            value = withContext(Dispatchers.IO) { decodeUriBitmap(context, uri, 3000) }
        }
        val loadedSharedBitmap = fullBitmap
        Dialog(
            onDismissRequest = {
                viewingSharedImageVisible = false
                scope.launch { delay(160); viewingSharedImage = null }
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visible = viewingSharedImageVisible,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140))
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable {
                    viewingSharedImageVisible = false
                    scope.launch { delay(160); viewingSharedImage = null }
                }, contentAlignment = Alignment.Center) {
                    if (loadedSharedBitmap != null) {
                        Image(loadedSharedBitmap.asImageBitmap(), contentDescription = "查看待处理图片",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f))
                    }
                }
            }
        }
    }

    if (viewingPhoto != null) {
        val photo = viewingPhoto!!
        val fullBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, photo.path) {
            value = withContext(Dispatchers.IO) { decodeImageBitmap(photo.path, 2600) }
        }
        val loadedFullBitmap = fullBitmap
        Dialog(onDismissRequest = { viewingPhoto = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewingPhoto = null }, contentAlignment = Alignment.Center) {
                if (loadedFullBitmap != null) {
                    Image(loadedFullBitmap, contentDescription = "查看原图",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f))
                }
            }
        }
    }

    if (confirmDeleteCategory != null) {
        val category = confirmDeleteCategory!!
        AlertDialog(onDismissRequest = { confirmDeleteCategory = null }, title = { Text("删除证件卡片？") },
            confirmButton = { TextButton(onClick = {
                flipResetToken++
                confirmDeleteCategory = null
                exitingCategories = exitingCategories + category
                scope.launch {
                    delay(200)
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
                    exitingCategories = exitingCategories - category
                }
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteCategory = null }) { Text("取消") } })
    }
    if (confirmDeleteMember != null) {
        val member = confirmDeleteMember!!
        AlertDialog(onDismissRequest = { confirmDeleteMember = null }, title = { Text("删除家庭成员？") },
            text = { Text("将从家庭成员列表中移除“" + member + "”。已导入的证件文件会保留在本机。") },
            confirmButton = { TextButton(onClick = {
                members = members.filterNot { it == member }
                prefs.edit().putString("members", JSONArray(members).toString()).apply()
                removeMemberData(member)
                confirmDeleteMember = null
            }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteMember = null }) { Text("取消") } })
    }
    if (addMember) {
        var addDialogEntered by remember { mutableStateOf(false) }
        val addDialogScale by animateFloatAsState(
            targetValue = if (addDialogEntered) 1f else .72f,
            animationSpec = spring(dampingRatio = .42f, stiffness = 360f),
            label = "member-capsule-jelly"
        )
        LaunchedEffect(Unit) { addDialogEntered = true }
        Dialog(onDismissRequest = { addMember = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = addDialogScale
                    scaleY = addDialogScale
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Column(
                    modifier = Modifier.width(240.dp).heightIn(min = 360.dp)
                        .clip(RoundedCornerShape(120.dp))
                        .background(Color.Black)
                        .padding(horizontal = 20.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier.size(184.dp).clip(CircleShape)
                            .background(Color(0xFFCAD8D9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(memberText.trim().firstOrNull()?.uppercase() ?: "+", color = Ink, fontSize = 42.sp, fontWeight = FontWeight.Medium)
                    }
                    OutlinedTextField(
                        value = memberText,
                        onValueChange = { memberText = it },
                        singleLine = true,
                        placeholder = { Text("姓名") },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White.copy(alpha = 0.65f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF333638))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { addMember = false }),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Close, contentDescription = "取消", tint = Color(0xFFFF8A80), modifier = Modifier.size(28.dp)) }
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFA8E8DC))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val n = memberText.trim()
                                    if (n.isNotEmpty() && n !in members) {
                                        members = members + n
                                        prefs.edit().putString("members", JSONArray(members).toString()).apply()
                                        selected = n
                                    }
                                    addMember = false
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Check, contentDescription = "确认", tint = Ink, modifier = Modifier.size(28.dp)) }
                }
            }
        }
    }
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
    onDropUris: (List<Uri>, String) -> Unit,
    modifier: Modifier = Modifier,
    resetFlipToken: Int,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onReorder: (String, Int) -> Unit,
    onImport: () -> Unit,
    onDelete: (Doc) -> Unit
) {
    val context = LocalContext.current
    val reorderThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 68.dp.toPx() }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .985f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 360f),
        label = "wallet-card-scale"
    )
    val cardShape = RoundedCornerShape(20.dp)
    var isDropTargeted by remember { mutableStateOf(false) }
    var isFoilApplying by remember { mutableStateOf(false) }
    var isReordering by remember(name) { mutableStateOf(false) }
    var reorderDragOffset by remember(name) { mutableFloatStateOf(0f) }
    var reorderTiltTarget by remember(name) { mutableFloatStateOf(0f) }
    val animatedReorderOffset by animateFloatAsState(
        targetValue = if (isReordering) reorderDragOffset else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card-reorder-offset"
    )
    val animatedReorderScale by animateFloatAsState(
        targetValue = if (isReordering) 1.035f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card-reorder-lift"
    )
    val animatedReorderTilt by animateFloatAsState(
        targetValue = if (isReordering) reorderTiltTarget else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "card-reorder-tilt"
    )
    val cardElevation by animateDpAsState(
        targetValue = when {
            isReordering -> 30.dp
            isDropTargeted || isFoilApplying -> 24.dp
            else -> 12.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "card-interaction-elevation"
    )
    val foilScope = rememberCoroutineScope()
    val foilProgress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isDropTargeted, isFoilApplying) {
        when {
            isFoilApplying -> {
                foilProgress.snapTo(0f)
                foilProgress.animateTo(1f, androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            isDropTargeted -> foilProgress.animateTo(1f, androidx.compose.animation.core.tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            else -> foilProgress.animateTo(0f, androidx.compose.animation.core.tween(220))
        }
    }
    val currentDropHandler by rememberUpdatedState(onDropUris)
    // The drag target object is remembered, so keep expansion state current inside its callbacks.
    val currentExpanded by rememberUpdatedState(isExpanded)
    var flipped by remember(name, resetFlipToken) { mutableStateOf(false) }
    val currentFlipped by rememberUpdatedState(flipped)
    val flipRotation by animateFloatAsState(targetValue = if (flipped) 180f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f), label = "card-flip-rotation")
    val firstImage = documents.firstOrNull { doc ->
        val ext = File(doc.path).extension.lowercase()
        (doc.side == (if (flipped) "back" else "front")) && ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif")
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, firstImage?.path) {
        value = firstImage?.let { doc ->
            withContext(Dispatchers.IO) { decodeImageBitmap(doc.path, 1400) }
        }
    }
    val loadedCardBitmap = bitmap
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
            .shadow(elevation = cardElevation, shape = cardShape, clip = false)
            .graphicsLayer {
                translationY = animatedReorderOffset
                scaleX = animatedReorderScale
                scaleY = animatedReorderScale
                rotationZ = animatedReorderTilt
            }
            .clip(cardShape)
            .drawWithContent {
                drawContent()
                val progress = foilProgress.value
                if (progress > 0.001f) {
                    val w = size.width
                    val h = size.height
                    val intensity = if (isFoilApplying) 1f else 0.92f
                    // High-contrast cold-to-warm holographic spectrum.
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xB84DE7E8), Color(0xA84D8CFF),
                                Color(0xB69D65F5), Color(0xB8F07BD4),
                                Color(0xB8FFB85C), Color(0xA8F8F1C9),
                                Color(0xA84DE7E8)
                            ),
                            start = Offset(-w * 0.2f, h * 0.1f),
                            end = Offset(w * 1.1f, h * 0.95f)
                        ),
                        alpha = progress * intensity,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                    )
                    // Saturated pearlescent color bands shift across the surface as the film settles.
                    val sweepX = w * (progress * 1.75f - 0.38f)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x4076F7F2),
                                Color(0xC8FFFFFF),
                                Color(0xA8B6F7FF),
                                Color(0x889A73FF),
                                Color(0x80FFB4D8),
                                Color(0x90FFD27A),
                                Color.Transparent
                            ),
                            start = Offset(sweepX - w * 0.46f, 0f),
                            end = Offset(sweepX + w * 0.46f, h)
                        ),
                        alpha = (0.72f + 0.28f * progress) * intensity,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                    )
                    // Clearly visible concentric security-print rings in alternating cool and warm hues.
                    val spacing = with(androidx.compose.ui.unit.Density(size.width / 360f)) { 27.dp.toPx() }
                    val radiusStep = maxOf(spacing, 1f)
                    val ringPalette = listOf(
                        Color(0xBFFFFFFF), Color(0xA86CEDE8),
                        Color(0xA8B9A0FF), Color(0xA8FFC0E6),
                        Color(0xA8FFD68A)
                    )
                    // Sparse multi-ring engraving keeps the holographic texture clear without thousands of draw calls per frame.
                    var row = -radiusStep
                    var rowIndex = 0
                    while (row < h + radiusStep) {
                        var col = -radiusStep
                        var colIndex = 0
                        while (col < w + radiusStep) {
                            val cx = col + if (rowIndex % 2 == 0) radiusStep * 0.55f else 0f
                            val cy = row
                            for (ring in 1..3) {
                                drawCircle(
                                    color = ringPalette[(ring + rowIndex + colIndex) % ringPalette.size]
                                        .copy(alpha = (0.28f + ring * 0.09f) * progress * intensity),
                                    radius = radiusStep * ring * 0.43f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = if (ring == 1) 1.15f else 0.85f),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                                )
                            }
                            col += radiusStep * 3.35f
                            colIndex++
                        }
                        row += radiusStep * 2.8f
                        rowIndex++
                    }
                    // Bright leading edge, like a real film being pressed from one side to the other.
                    val edgeX = w * (progress * 1.7f - 0.28f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xA8A7FFF7),
                                Color.White,
                                Color(0xB8FFD7F4),
                                Color.Transparent
                            ),
                            startX = edgeX - w * 0.075f,
                            endX = edgeX + w * 0.075f
                        ),
                        alpha = (0.5f + 0.5f * progress) * intensity,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Screen
                    )
                }
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    runCatching {
                        val androidEvent = event.toAndroidDragEvent()
                        val clip = androidEvent.clipData
                        val hasUri = clip != null && (0 until clip.itemCount).any { index ->
                            val item = clip.getItemAt(index)
                            item.uri != null || item.intent?.data != null ||
                                item.text?.toString()?.startsWith("content://") == true
                        }
                        hasUri || event.mimeTypes().any { type ->
                            type.startsWith("image/") || type == "text/uri-list" ||
                                type == "application/vnd.android.cursor.item/image" ||
                                type == "application/octet-stream"
                        }
                    }.getOrDefault(false)
                },
                target = remember {
                    object : DragAndDropTarget {
                        override fun onStarted(event: DragAndDropEvent) { isDropTargeted = false }
                        override fun onEntered(event: DragAndDropEvent) { isDropTargeted = true }
                        override fun onMoved(event: DragAndDropEvent) { isDropTargeted = true }
                        override fun onExited(event: DragAndDropEvent) { isDropTargeted = false }
                        override fun onEnded(event: DragAndDropEvent) { isDropTargeted = false }
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            isDropTargeted = false
                            val androidEvent = event.toAndroidDragEvent()
                            val permission = runCatching {
                                (context as? Activity)?.requestDragAndDropPermissions(androidEvent)
                            }.getOrNull()
                            return try {
                                val clip = androidEvent.clipData ?: return false
                                val uris = (0 until clip.itemCount).mapNotNull { index ->
                                    val item = clip.getItemAt(index)
                                    item.uri ?: item.intent?.data
                                        ?: item.text?.toString()?.takeIf { it.startsWith("content://") }?.let(Uri::parse)
                                }.distinct()
                                if (uris.isEmpty()) false else {
                                    isFoilApplying = true
                                    currentDropHandler(uris, if (currentExpanded && currentFlipped) "back" else "front")
                                    foilScope.launch {
                                        delay(1250)
                                        isFoilApplying = false
                                    }
                                    true
                                }
                            } catch (_: Exception) {
                                false
                            } finally {
                                permission?.release()
                            }
                        }
                    }
                }
            )
            .pointerInput(name, resetFlipToken, isExpanded) {
                var horizontalDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDistance = 0f },
                    onDragEnd = {
                        if (isExpanded && horizontalDistance.absoluteValue > 48f) flipped = !currentFlipped
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDistance += dragAmount
                    }
                )
            }
            .pointerInput(name) {
                var accumulatedDrag = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { accumulatedDrag = 0f; reorderDragOffset = 0f; reorderTiltTarget = 0f; isReordering = true },
                    onDragEnd = { accumulatedDrag = 0f; isReordering = false; reorderTiltTarget = 0f },
                    onDragCancel = { accumulatedDrag = 0f; isReordering = false; reorderTiltTarget = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        reorderDragOffset += amount.y
                        reorderTiltTarget = (amount.x / size.width.coerceAtLeast(1) * 18f).coerceIn(-2.5f, 2.5f)
                        accumulatedDrag += amount.y
                        if (kotlin.math.abs(accumulatedDrag) >= reorderThresholdPx) {
                            val direction = if (accumulatedDrag > 0f) 1 else -1
                            onReorder(name, direction)
                            reorderDragOffset -= direction * reorderThresholdPx
                            accumulatedDrag -= direction * reorderThresholdPx
                        }
                    }
                )
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
                onLongClick = null
            )
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationY = flipRotation; cameraDistance = 18 * density }.background(Brush.linearGradient(gradient))) {
            if (loadedCardBitmap != null) {
                Image(bitmap = loadedCardBitmap, contentDescription = name, contentScale = ContentScale.Crop,
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

private fun decodeImageBitmap(path: String, maxDimension: Int): androidx.compose.ui.graphics.ImageBitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }.getOrNull()
}

private fun decodeUriBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

private fun exportOrRequestGallery(context: Context, file: File, setPending: (File) -> Unit, requestPermission: () -> Unit, done: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= 29) done(exportImageToGallery(context, file))
    else if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) done(exportImageToGallery(context, file))
    else { setPending(file); requestPermission() }
}

private fun exportImageToGallery(context: Context, source: File): Boolean {
    if (!source.exists() || source.length() == 0L) return false
    return runCatching {
        val now = System.currentTimeMillis()
        val extension = source.extension.ifBlank { "jpg" }
        val mime = when (extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date(now))
        val displayName = "FamilyDocs_$timestamp.$extension"
        fun stampImageTime(file: File) {
            // Preserve the image while resetting its capture time so gallery apps sort it as a fresh export.
            runCatching {
                android.media.ExifInterface(file.absolutePath).apply {
                    val exifTime = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(now))
                    setAttribute(android.media.ExifInterface.TAG_DATETIME, exifTime)
                    setAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
                    setAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED, exifTime)
                    saveAttributes()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FamilyDocs")
                put(MediaStore.Images.Media.DATE_TAKEN, now)
                put(MediaStore.Images.Media.DATE_ADDED, now / 1000L)
                put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000L)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: return false
                // Update EXIF in the pending media item when the provider exposes a writable file descriptor.
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                        android.media.ExifInterface(descriptor.fileDescriptor).apply {
                            val exifTime = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(now))
                            setAttribute(android.media.ExifInterface.TAG_DATETIME, exifTime)
                            setAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL, exifTime)
                            setAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED, exifTime)
                            saveAttributes()
                        }
                    }
                }
                values.clear()
                values.put(MediaStore.Images.Media.DATE_TAKEN, now)
                values.put(MediaStore.Images.Media.DATE_ADDED, now / 1000L)
                values.put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000L)
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FamilyDocs").apply { mkdirs() }
            val target = File(dir, displayName)
            source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            stampImageTime(target)
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
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) {
        listOf("Juliet", "Kevin", "Martha", "James")
    }
}
private fun readCategories(prefs: android.content.SharedPreferences): List<String> {
    val defaults = listOf("Medical Documents", "Educational Documents", "Vehicle Documents", "Personal Documents", "Other Documents")
    return try {
        val raw = prefs.getString("categories", null) ?: return defaults
        val a = JSONArray(raw)
        (0 until a.length()).map { a.getString(it) }
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
