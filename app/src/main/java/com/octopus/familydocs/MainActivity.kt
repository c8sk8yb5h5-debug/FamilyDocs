package com.octopus.familydocs

import android.content.Context
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = uris.mapNotNull { importFile(context, it, importCategory, selected) }
        docs = docs + added
        writeDocs(prefs, docs)
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Surface(color = HeaderBg, shape = RoundedCornerShape(42.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.height(76.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dashboard, "Home", Modifier.size(28.dp).clickable { scroll.animateScrollTo(0) }, tint = Color.White)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Family Documents", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(docs.size.toString() + " Documents", color = Color.LightGray, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.Settings, "Settings", Modifier.size(28.dp).clickable { showSettings = true }, tint = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                members.forEachIndexed { index, member ->
                    val selectedScale by animateFloatAsState(if (member == selected) 1f else .94f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "member")
                    Column(Modifier.scale(selectedScale).clip(RoundedCornerShape(34.dp)).background(if (member == selected) Color.Black else Color.Transparent).clickable { selected = member }.padding(horizontal = 9.dp, vertical = if (member == selected) 9.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = listOf(Color(0xFFE8D9C6), Color(0xFFCAD8D9), Color(0xFFD9D1E8), Color(0xFFE9D3CC))[index % 4], shape = CircleShape, modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(member.firstOrNull()?.uppercase() ?: "•", color = Ink, fontSize = 24.sp) }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(member, color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
                Column(Modifier.width(64.dp).clickable { memberText = ""; addMember = true }, horizontalAlignment = Alignment.CenterHorizontally) {
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
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Settings") }, text = { Text("Documents stay on this device and are not uploaded to the cloud.") }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
}

@Composable
private fun DocumentWaveCard(
    name: String, count: Int, tint: Color, icon: ImageVector, expanded: Boolean,
    documents: List<Doc>, onClick: () -> Unit, onImport: () -> Unit, onDelete: (Doc) -> Unit, index: Int
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .985f else if (expanded) 1f else .992f, spring(dampingRatio = .72f, stiffness = 360f), label = "card-scale")
    Surface(color = tint, shape = StackWave(index), modifier = Modifier.fillMaxWidth().offset(y = if (index == 0) 0.dp else (-17).dp).zIndex(index.toFloat()).scale(scale).animateContentSize(spring(dampingRatio = .78f, stiffness = 260f))) {
        Column(Modifier.clickable(interactionSource = source, indication = null, onClick = onClick).padding(start = 20.dp, end = 16.dp, top = 25.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFF9F9F6), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Ink, modifier = Modifier.size(26.dp)) }
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(count.toString().padStart(2, '0') + " Documents", color = Ink.copy(alpha = .84f), fontSize = 12.sp)
                }
                if (index == 0 || name == "Personal Documents") {
                    Surface(color = Color(0xFFF9F9F6), shape = CircleShape, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Lock, "Stored locally", tint = Ink, modifier = Modifier.size(21.dp)) }
                    }
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 260f)) + fadeIn(), exit = shrinkVertically(spring(dampingRatio = .9f, stiffness = 300f)) + fadeOut()) {
                Column(Modifier.fillMaxWidth().padding(start = 62.dp, top = 8.dp, bottom = 4.dp)) {
                    if (documents.isEmpty()) Text("No documents yet", color = Ink.copy(alpha = .72f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    documents.forEach { doc ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, null, tint = Ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(doc.name, color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onDelete(doc) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("Delete", color = Ink, fontSize = 11.sp) }
                        }
                    }
                    Button(onClick = onImport, shape = RoundedCornerShape(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Add Document", fontSize = 12.sp)
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
private fun readMembers(prefs: android.content.SharedPreferences): List<String> = try {
    val raw = prefs.getString("members", null) ?: return listOf("Juliet", "Kevin", "Martha", "James")
    val a = JSONArray(raw); (0 until a.length()).map { a.getString(it) }.ifEmpty { listOf("Juliet", "Kevin", "Martha", "James") }
} catch (_: Exception) { listOf("Juliet", "Kevin", "Martha", "James") }
private fun readCategories(prefs: android.content.SharedPreferences): List<String> = try {
    val defaults = listOf("Medical Documents", "Educational Documents", "Vehicle Documents", "Personal Documents", "Other Documents")
    val raw = prefs.getString("categories", null) ?: return defaults
    val a = JSONArray(raw); (0 until a.length()).map { a.getString(it) }.ifEmpty { defaults }
} catch (_: Exception) { listOf("Medical Documents", "Educational Documents", "Vehicle Documents", "Personal Documents", "Other Documents") }

private fun importFile(context: Context, uri: Uri, category: String, owner: String): Doc? = try {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst() && i >= 0) cursor.getString(i) else null
    } ?: "document"
    val dir = File(context.filesDir, "documents").apply { mkdirs() }
    val target = File(dir, System.currentTimeMillis().toString() + "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    val input = context.contentResolver.openInputStream(uri) ?: return null
    input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
    Doc(name, target.absolutePath, category, owner)
} catch (_: Exception) { null }
private fun readDocs(prefs: android.content.SharedPreferences): List<Doc> = try {
    val a = JSONArray(prefs.getString("docs", "[]"))
    (0 until a.length()).map { a.getJSONObject(it).let { o -> Doc(o.getString("name"), o.getString("path"), o.getString("category"), o.getString("owner")) } }.filter { File(it.path).exists() }
} catch (_: Exception) { emptyList() }
private fun writeDocs(prefs: android.content.SharedPreferences, docs: List<Doc>) {
    val a = JSONArray()
    docs.forEach { a.put(JSONObject().put("name", it.name).put("path", it.path).put("category", it.category).put("owner", it.owner)) }
    prefs.edit().putString("docs", a.toString()).apply()
}
