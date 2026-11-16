package com.khalil.noor.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khalil.noor.lock.DailyScheduler
import com.khalil.noor.lock.HijriDate
import com.khalil.noor.lock.LockPrefs
import com.khalil.noor.lock.LockStyle
import com.khalil.noor.lock.WallpaperRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(LockPrefs.load(ctx)) }
    var photoVer by remember { mutableIntStateOf(0) }
    var hasPhoto by remember { mutableStateOf(LockPrefs.photoFile(ctx).let { it.exists() && it.length() > 0 }) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var waitingPermission by remember { mutableStateOf(false) }
    val (sw, sh) = remember { WallpaperRenderer.screenSize(ctx) }

    fun update(s: LockStyle) {
        style = s
        LockPrefs.save(ctx, s)
    }

    LaunchedEffect(style, photoVer) {
        preview = withContext(Dispatchers.Default) {
            val w = 540
            val h = (w * sh.toFloat() / sw).toInt()
            WallpaperRenderer.render(ctx, style, HijriDate.today(style.hijriAdjust), w, h)
        }
    }

    fun photoReady() {
        hasPhoto = true
        photoVer++
        update(style.copy(usePhoto = true))
    }

    fun importWallpaper(askPermission: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { WallpaperRenderer.importCurrentWallpaper(ctx) }
            if (ok) {
                photoReady()
                Toast.makeText(ctx, "تم أخذ خلفيتك الحالية ✓", Toast.LENGTH_SHORT).show()
            } else if (askPermission && Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                Toast.makeText(ctx, "فعّلي «السماح بإدارة كل الملفات» لتطبيق إيمان وارجعي", Toast.LENGTH_LONG).show()
                waitingPermission = true
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            } else {
                Toast.makeText(ctx, "ما قدرت آخذ الخلفية الحالية، اختاري الصورة من الاستوديو", Toast.LENGTH_LONG).show()
            }
        }
    }

    // لما ترجع من صفحة الصلاحية نحاول مرة ثانية
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && waitingPermission) {
                waitingPermission = false
                importWallpaper(askPermission = false)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    LockPrefs.photoFile(ctx).outputStream().use { input.copyTo(it) }
                }
                withContext(Dispatchers.Main) { photoReady() }
            }
        }
    }

    val today = remember(style.hijriAdjust) { HijriDate.today(style.hijriAdjust) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(
            "شاشة القفل",
            "${today.dayName} ${HijriDate.digits(today.hijriDay, true)} ${today.hijriMonth} " +
                "${HijriDate.digits(today.hijriYear, true)} هـ  •  " +
                "${HijriDate.digits(today.gregDay, true)} ${today.gregMonth} ${HijriDate.digits(today.gregYear, true)} م",
        )

        // المعاينة
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier.fillMaxWidth(0.62f).aspectRatio(sw.toFloat() / sh)
                    .shadow(10.dp, shape).clip(shape)
                    .border(BorderStroke(5.dp, Color(0xFF111111)), shape)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                val b = preview
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CircularProgressIndicator()
                Text("٩:٤١", color = Color.White.copy(alpha = 0.6f), fontSize = 30.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
            }
        }

        Section("الخلفية") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = style.usePhoto, onClick = { update(style.copy(usePhoto = true)) },
                    label = { Text("خلفيتي / صورة") }, modifier = Modifier.weight(1f))
                FilterChip(selected = !style.usePhoto, onClick = { update(style.copy(usePhoto = false)) },
                    label = { Text("لون بنفس البطاقة") }, modifier = Modifier.weight(1f))
            }
            if (style.usePhoto) {
                Text(
                    if (hasPhoto) "البطاقة تنحط فوق صورتك، وباقي الشاشة تبان زي ما هي."
                    else "اختاري خلفيتك عشان تنحط البطاقة فوقها.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { importWallpaper(askPermission = true) },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("خلفية الجوال الحالية") }
                    OutlinedButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("صورة من الاستوديو") }
                }
                if (LockPrefs.lastApplied(ctx) != null) {
                    Text("ملاحظة: بعد ما تطبّقين، خلفية الجوال الحالية يكون فيها البطاقة. " +
                        "لو تبين تغيّرين الصورة استخدمي «صورة من الاستوديو».",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Section("التاريخ") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "هجري وميلادي", 1 to "هجري", 2 to "ميلادي").forEach { (v, label) ->
                    FilterChip(selected = style.dates == v, onClick = { update(style.copy(dates = v)) },
                        label = { Text(label) }, modifier = Modifier.weight(if (v == 0) 1.4f else 1f))
                }
            }
        }

        Section("البطاقة") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = style.cardSize == 1, onClick = { update(style.copy(cardSize = 1)) },
                    label = { Text("كبيرة") }, modifier = Modifier.weight(1f))
                FilterChip(selected = style.cardSize == 0, onClick = { update(style.copy(cardSize = 0)) },
                    label = { Text("صغيرة") }, modifier = Modifier.weight(1f))
            }
            Text("اللون", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                WallpaperRenderer.THEMES.forEachIndexed { i, t ->
                    val selected = style.theme == i
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape)
                                .background(
                                    if (t.glass) Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x33000000)))
                                    else Brush.verticalGradient(listOf(Color(t.top), Color(t.mid), Color(t.bottom)))
                                )
                                .border(
                                    if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape,
                                )
                                .clickable { update(style.copy(theme = i)) },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(t.accent))) }
                        Text(t.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Text("الخط", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperRenderer.FONTS.forEachIndexed { i, (name, res) ->
                    FilterChip(
                        selected = style.font == i,
                        onClick = { update(style.copy(font = i)) },
                        label = { Text(name, fontFamily = FontFamily(Font(res)), fontSize = 17.sp) },
                    )
                }
            }
        }

        Section("مكان البطاقة") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("فوق", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = style.posY, onValueChange = { style = style.copy(posY = it) },
                    onValueChangeFinished = { LockPrefs.save(ctx, style) },
                    valueRange = 0.12f..0.88f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("تحت", style = MaterialTheme.typography.bodySmall)
            }
        }

        Section("عبارة تحت التاريخ (اختياري)") {
            OutlinedTextField(
                value = style.extraText,
                onValueChange = { update(style.copy(extraText = it.take(40))) },
                placeholder = { Text("مثلاً: اللهم صلّ على محمد") },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("خيارات") {
            Toggle("أرقام عربية (٢٥ بدل 25)", style.arabicDigits) { update(style.copy(arabicDigits = it)) }
            Toggle("نفس الخلفية للشاشة الرئيسية", style.alsoHome) { update(style.copy(alsoHome = it)) }
            Toggle("تحديث تلقائي كل يوم بعد منتصف الليل", style.autoDaily) {
                update(style.copy(autoDaily = it)); DailyScheduler.sync(ctx)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تصحيح الهجري", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                listOf(-1 to "−١", 0 to "٠", 1 to "+١").forEach { (v, label) ->
                    FilterChip(selected = style.hijriAdjust == v, onClick = { update(style.copy(hijriAdjust = v)) },
                        label = { Text(label) }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Text("الهجري على تقويم أم القرى. لو اختلف يوم بسبب الرؤية صحّحيه من هنا.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { DailyScheduler.applyNow(ctx) }
                    DailyScheduler.sync(ctx)
                    busy = false
                    Toast.makeText(ctx,
                        if (ok) "تم ✓ اقفلي الشاشة وشوفي" else "ما قدرت أغيّر الخلفية، جرّبي مرة ثانية",
                        Toast.LENGTH_LONG).show()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("تطبيق على شاشة القفل", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!value) }) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
