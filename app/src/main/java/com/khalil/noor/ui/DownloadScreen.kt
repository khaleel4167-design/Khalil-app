package com.khalil.noor.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.khalil.noor.dl.Choice
import com.khalil.noor.dl.DlState
import com.khalil.noor.dl.Downloader
import com.khalil.noor.dl.MediaInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen() {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val state by Downloader.state.collectAsState()
    val engine by Downloader.engine.collectAsState()
    var url by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("تحميل من يوتيوب وتيك توك", "الصقي الرابط، أو شاركي الفيديو من التطبيق واختاري «نور»")

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://…") },
            label = { Text("رابط الفيديو") },
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); Downloader.fetch(url) }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    if (t.isNotBlank()) { url = t; focus.clearFocus(); Downloader.fetch(t) }
                },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("لصق وجلب") }
            Button(
                onClick = { focus.clearFocus(); Downloader.fetch(url) },
                enabled = url.isNotBlank() && state !is DlState.Fetching && state !is DlState.Downloading,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("جلب") }
        }

        when (val s = state) {
            DlState.Idle -> HintCard()
            DlState.Fetching -> Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text("يجيب معلومات الفيديو…")
            }
            is DlState.Ready -> ChooseCard(s.info) { choice -> Downloader.download(ctx, s.info, choice) }
            is DlState.Downloading -> ProgressCard(s)
            is DlState.Done -> DoneCard(s)
            is DlState.Error -> {
                ErrorCard(s.message)
                s.info?.let { info -> ChooseCard(info) { choice -> Downloader.download(ctx, info, choice) } }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("محرك التحميل: $engine", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { Downloader.updateEngine(ctx) }) { Text("تحديث محرك التحميل") }
        }
    }
}

@Composable
fun Header(title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HintCard() {
    SoftCard {
        Text("طريقة سريعة", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "في يوتيوب أو تيك توك اضغطي «مشاركة» ثم اختاري «نور»، والتطبيق يجيب الفيديو على طول.\n" +
                "الفيديو ينحفظ في الاستوديو (مجلد Movies/Noor)، والصوت في الموسيقى (Music/Noor).",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SoftCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun InfoHeader(info: MediaInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (info.thumb != null) {
            Box {
                AsyncImage(
                    model = info.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                )
                if (info.duration > 0) {
                    Text(
                        duration(info.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(androidx.compose.ui.graphics.Color(0xAA000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        info.uploader?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChooseCard(info: MediaInfo, onDownload: (Choice) -> Unit) {
    var audio by rememberSaveable(info.url) { mutableStateOf(false) }
    val heights = info.heights.filter { it <= 2160 }
    var height by rememberSaveable(info.url) {
        mutableIntStateOf(heights.firstOrNull { it <= 720 } ?: heights.firstOrNull() ?: 0)
    }
    var mp3 by rememberSaveable(info.url) { mutableStateOf(true) }

    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoHeader(info)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = !audio, onClick = { audio = false }, label = { Text("فيديو") },
                    modifier = Modifier.weight(1f))
                FilterChip(selected = audio, onClick = { audio = true }, label = { Text("صوت فقط") },
                    modifier = Modifier.weight(1f))
            }

            Text("الجودة", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!audio) {
                    if (heights.isEmpty()) {
                        FilterChip(selected = true, onClick = {}, label = { Text("أفضل جودة") })
                    } else {
                        heights.forEach { h ->
                            FilterChip(selected = height == h, onClick = { height = h },
                                label = { Text(qualityLabel(h)) })
                        }
                    }
                } else {
                    FilterChip(selected = mp3, onClick = { mp3 = true }, label = { Text("MP3 · يشتغل في كل مكان") })
                    FilterChip(selected = !mp3, onClick = { mp3 = false }, label = { Text("M4A · أسرع") })
                }
            }

            Button(
                onClick = { onDownload(if (audio) Choice.Audio(mp3) else Choice.Video(if (heights.isEmpty()) 0 else height)) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("تحميل", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun ProgressCard(s: DlState.Downloading) {
    SoftCard {
        InfoHeader(s.info)
        val p = s.progress / 100f
        if (s.progress <= 0f) LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
        else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.progress.toInt()}٪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            if (s.eta > 0) Text("باقي ${duration(s.eta.toInt())}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { Downloader.cancel() }) { Text("إلغاء") }
        }
        Text(s.line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Left,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DoneCard(s: DlState.Done) {
    val ctx = LocalContext.current
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("تم التحميل ✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(s.info.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (s.audio) "محفوظ في الموسيقى ← Music/Noor" else "محفوظ في الاستوديو ← Movies/Noor",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    s.uri?.let {
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(it, if (s.audio) "audio/*" else "video/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { ctx.startActivity(i) }
                    }
                }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("تشغيل") }
                OutlinedButton(onClick = {
                    s.uri?.let {
                        val i = Intent(Intent.ACTION_SEND).setType(if (s.audio) "audio/*" else "video/*")
                            .putExtra(Intent.EXTRA_STREAM, it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { ctx.startActivity(Intent.createChooser(i, "مشاركة")) }
                    }
                }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("مشاركة") }
            }
            TextButton(onClick = { Downloader.reset() }) { Text("تحميل فيديو ثاني") }
        }
    }
}

@Composable
private fun ErrorCard(msg: String) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium)
    }
}

private fun qualityLabel(h: Int) = when (h) {
    2160 -> "4K"
    1440 -> "2K"
    1080 -> "1080p · عالية"
    720 -> "720p"
    480 -> "480p"
    360 -> "360p · خفيفة"
    else -> "${h}p"
}

private fun duration(sec: Int): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
