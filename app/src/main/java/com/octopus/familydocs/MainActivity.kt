package com.octopus.familydocs

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Bg = Color(0xFF171819)
private val CardBg = Color(0xFF292B2D)
private val Pastels = listOf(Color(0xFFC9E5F4), Color(0xFFCBE9D7), Color(0xFFF3E5A8), Color(0xFFF0D2D5))
private data class Doc(val name: String, val path: String, val category: String, val owner: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = CardBg)) { Home() } }
    }
}

@Composable
private fun Home() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("familydocs", Context.MODE_PRIVATE) }
    var members by remember { mutableStateOf(readMembers(prefs)) }
    var selected by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf("身份证件") }
    var query by remember { mutableStateOf("") }
    var newMember by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(false) }
    var docs by remember { mutableStateOf(readDocs(prefs)) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> importFile(context, uri, expanded, members.getOrElse(selected) { "我" })?.let { docs = docs + it } }
        writeDocs(prefs, docs)
    }
    val categories = listOf("身份证件", "银行卡", "家庭资料", "其他证件")
    val subtitles = listOf("身份证、护照、户口簿", "银行卡与支付相关证件", "出生证明、结婚证等", "保单、资格证及其他文件")
    val icons = listOf(Icons.Default.Badge, Icons.Default.CreditCard, Icons.Default.Folder, Icons.Default.Person)
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FAMILY ARCHIVE", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp)
                Text("家庭证件库", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(color = CardBg, shape = RoundedCornerShape(50), onClick = { dialog = true }) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("成员", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            members.forEachIndexed { i, name ->
                val scale by animateFloatAsState(if (i == selected) 1.05f else 1f, spring(), label = "member")
                Column(Modifier.scale(scale).clickable { selected = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = if (i == selected) Pastels[0] else CardBg, modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = if (i == selected) Bg else Color.White) }
                    }
                    Spacer(Modifier.height(5.dp)); Text(name, color = if (i == selected) Color.White else Color.Gray, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("搜索证件或文件") })
        Spacer(Modifier.height(16.dp))
        Text("证件分类", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(categories) { category ->
                val i = categories.indexOf(category)
                val open = expanded == category
                val count = docs.count { it.category == category && it.owner == members.getOrElse(selected) { "我" } }
                val scale by animateFloatAsState(if (open) 1f else .985f, spring(dampingRatio = .72f), label = "category")
                Surface(color = Pastels[i], shape = WaveShape(), modifier = Modifier.fillMaxWidth().scale(scale).clickable { expanded = if (open) "" else category }) {
                    Column(Modifier.animateContentSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icons[i], null, tint = Bg, modifier = Modifier.size(27.dp))
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(category, color = Bg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text(subtitles[i], color = Bg.copy(alpha = .7f), fontSize = 11.sp)
                            }
                            Text(count.toString(), color = Bg, fontSize = 13.sp)
                        }
                        if (open) {
                            Spacer(Modifier.height(10.dp))
                            val visible = docs.filter { it.category == category && it.owner == members.getOrElse(selected) { "我" } && it.name.contains(query, true) }
                            if (visible.isEmpty()) Text("暂无文件", color = Bg.copy(alpha = .7f), fontSize = 12.sp)
                            visible.forEach { doc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(doc.name, color = Bg, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                    TextButton(onClick = { File(doc.path).delete(); docs = docs.filterNot { it == doc }; writeDocs(prefs, docs) }) { Text("删除", color = Bg) }
                                }
                            }
                            Button(onClick = { import.launch(arrayOf("image/*", "application/pdf")) }, colors = ButtonDefaults.buttonColors(containerColor = Bg, contentColor = Color.White), shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("导入文件")
                            }
                        }
                    }
                }
            }
        }
    }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("添加家庭成员") }, text = { OutlinedTextField(newMember, { newMember = it }, label = { Text("成员称呼") }, singleLine = true) }, confirmButton = {
        TextButton(onClick = { if (newMember.isNotBlank()) { members = members + newMember.trim(); prefs.edit().putString("members", JSONArray(members).toString()).apply(); newMember = "" }; dialog = false }) { Text("添加") }
    }, dismissButton = { TextButton(onClick = { dialog = false }) { Text("取消") } })
}

private class WaveShape : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(16f, 0f); lineTo(size.width - 16f, 0f); quadraticTo(size.width, 0f, size.width, 16f)
            lineTo(size.width, size.height - 18f); quadraticTo(size.width * .72f, size.height - 2f, size.width * .45f, size.height - 15f)
            quadraticTo(size.width * .2f, size.height - 28f, 0f, size.height - 12f); lineTo(0f, 16f); quadraticTo(0f, 0f, 16f, 0f); close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(p)
    }
}
private fun readMembers(prefs: android.content.SharedPreferences): List<String> {
    return try {
        val a = JSONArray(prefs.getString("members", null) ?: return listOf("我", "伴侣", "孩子"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { listOf("我", "伴侣", "孩子") }
}
private fun importFile(context: Context, uri: Uri, category: String, owner: String): Doc? {
    return try {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "document"
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val target = File(dir, System.currentTimeMillis().toString() + "_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
        Doc(name, target.absolutePath, category, owner)
    } catch (_: Exception) { null }
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
