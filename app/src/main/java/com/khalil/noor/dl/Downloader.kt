package com.khalil.noor.dl

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class MediaInfo(
    val url: String,
    val title: String,
    val thumb: String?,
    val uploader: String?,
    val duration: Int,
    val heights: List<Int>,
)

sealed interface Choice {
    /** height = 0 تعني أفضل جودة متوفرة */
    data class Video(val height: Int) : Choice
    data class Audio(val mp3: Boolean) : Choice
}

sealed interface DlState {
    data object Idle : DlState
    data object Fetching : DlState
    data class Ready(val info: MediaInfo) : DlState
    data class Downloading(val info: MediaInfo, val choice: Choice, val progress: Float, val eta: Long, val line: String) : DlState
    data class Done(val info: MediaInfo, val audio: Boolean, val uri: Uri?, val name: String) : DlState
    data class Error(val message: String, val info: MediaInfo?) : DlState
}

object Downloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Boolean>()
    private val _state = MutableStateFlow<DlState>(DlState.Idle)
    val state: StateFlow<DlState> = _state

    private val _engine = MutableStateFlow("جاري التجهيز…")
    val engine: StateFlow<String> = _engine

    private var job: Job? = null
    private var processId: String? = null

    fun init(c: Context) {
        val app = c.applicationContext
        scope.launch {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready.complete(true)
                _engine.value = "الإصدار " + (YoutubeDL.getInstance().version(app) ?: "؟")
                // تحديث تلقائي للمحرك كل 3 أيام
                val p = app.getSharedPreferences("dl", Context.MODE_PRIVATE)
                val last = p.getLong("lastUpdate", 0L)
                if (System.currentTimeMillis() - last > 3L * 24 * 3600 * 1000) {
                    runCatching { YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE) }
                        .onSuccess {
                            p.edit().putLong("lastUpdate", System.currentTimeMillis()).apply()
                            _engine.value = "الإصدار " + (YoutubeDL.getInstance().version(app) ?: "؟")
                        }
                }
            } catch (e: Throwable) {
                ready.complete(false)
                _engine.value = "تعذّر تجهيز محرك التحميل"
            }
        }
    }

    fun reset() {
        if (_state.value !is DlState.Downloading) _state.value = DlState.Idle
    }

    fun fetch(rawUrl: String) {
        val url = extractUrl(rawUrl) ?: run {
            _state.value = DlState.Error("ما لقيت رابط صحيح. انسخي رابط الفيديو من يوتيوب أو تيك توك.", null)
            return
        }
        job?.cancel()
        _state.value = DlState.Fetching
        job = scope.launch {
            if (!ready.await()) {
                _state.value = DlState.Error("محرك التحميل ما اشتغل. سكّري التطبيق وافتحيه من جديد.", null)
                return@launch
            }
            try {
                val req = YoutubeDLRequest(url).apply { addOption("--no-playlist") }
                val v = YoutubeDL.getInstance().getInfo(req)
                val heights = (v.formats ?: arrayListOf())
                    .filter { (it.vcodec ?: "none") != "none" && it.height > 0 }
                    .map { normalizeHeight(it.height) }
                    .distinct()
                    .sortedDescending()
                _state.value = DlState.Ready(
                    MediaInfo(
                        url = url,
                        title = v.title ?: "بدون عنوان",
                        thumb = v.thumbnail,
                        uploader = v.uploader,
                        duration = v.duration,
                        heights = heights,
                    )
                )
            } catch (e: Throwable) {
                _state.value = DlState.Error(friendly(e), null)
            }
        }
    }

    fun download(c: Context, info: MediaInfo, choice: Choice) {
        val app = c.applicationContext
        job?.cancel()
        val id = UUID.randomUUID().toString()
        processId = id
        _state.value = DlState.Downloading(info, choice, 0f, 0, "يبدأ التحميل…")
        ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))

        job = scope.launch {
            val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "dl/$id").apply { mkdirs() }
            try {
                val req = YoutubeDLRequest(info.url)
                req.addOption("--no-playlist")
                req.addOption("--no-mtime")
                req.addOption("-o", dir.absolutePath + "/%(title).80B.%(ext)s")
                when (choice) {
                    is Choice.Video -> {
                        val h = choice.height
                        val f = if (h > 0)
                            "bv*[height<=$h][vcodec^=avc]+ba[ext=m4a]/bv*[height<=$h]+ba/b[height<=$h]/bv*+ba/b"
                        else "bv*[vcodec^=avc]+ba[ext=m4a]/bv*+ba/b"
                        req.addOption("-f", f)
                        req.addOption("--merge-output-format", "mp4")
                    }
                    is Choice.Audio -> {
                        req.addOption("-f", if (choice.mp3) "ba/b" else "ba[ext=m4a]/ba/b")
                        req.addOption("-x")
                        req.addOption("--audio-format", if (choice.mp3) "mp3" else "m4a")
                        req.addOption("--audio-quality", "0")
                    }
                }

                YoutubeDL.getInstance().execute(req, id) { progress, eta, line ->
                    _state.value = DlState.Downloading(info, choice, progress.coerceIn(0f, 100f), eta, line)
                }

                val file = dir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("ما انحفظ أي ملف")

                _state.value = DlState.Downloading(info, choice, 100f, 0, "يحفظ في الجوال…")
                val audio = choice is Choice.Audio
                val uri = saveToGallery(app, file, audio)
                _state.value = DlState.Done(info, audio, uri, file.name)
            } catch (e: YoutubeDL.CanceledException) {
                _state.value = DlState.Ready(info)
            } catch (e: Throwable) {
                if (_state.value is DlState.Downloading) _state.value = DlState.Error(friendly(e), info)
            } finally {
                dir.deleteRecursively()
                processId = null
            }
        }
    }

    fun cancel() {
        val st = _state.value
        processId?.let { runCatching { YoutubeDL.getInstance().destroyProcessById(it) } }
        job?.cancel()
        if (st is DlState.Downloading) _state.value = DlState.Ready(st.info)
    }

    /** تحديث محرك التحميل يدوياً */
    fun updateEngine(c: Context) {
        val app = c.applicationContext
        scope.launch {
            _engine.value = "جاري التحديث…"
            if (!ready.await()) { _engine.value = "تعذّر تجهيز محرك التحميل"; return@launch }
            try {
                val st = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                app.getSharedPreferences("dl", Context.MODE_PRIVATE).edit()
                    .putLong("lastUpdate", System.currentTimeMillis()).apply()
                val v = YoutubeDL.getInstance().version(app) ?: "؟"
                _engine.value = if (st == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE)
                    "محدّث (الإصدار $v)" else "تم التحديث (الإصدار $v)"
            } catch (e: Throwable) {
                _engine.value = "فشل التحديث، تأكدي من الإنترنت"
            }
        }
    }

    // ------------------------------------------------------------------

    private fun normalizeHeight(h: Int): Int = when {
        h >= 2000 -> 2160
        h >= 1400 -> 1440
        h >= 1000 -> 1080
        h >= 700 -> 720
        h >= 470 -> 480
        h >= 350 -> 360
        h >= 230 -> 240
        else -> 144
    }

    fun extractUrl(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')

    private fun friendly(e: Throwable): String {
        val m = (e.message ?: e.toString())
        return when {
            m.contains("Sign in to confirm", true) || m.contains("bot", true) ->
                "يوتيوب طلب تأكيد. اضغطي \"تحديث محرك التحميل\" تحت وجربي مرة ثانية."
            m.contains("Unable to resolve host", true) || m.contains("network", true) ||
                m.contains("timed out", true) -> "تأكدي من اتصال الإنترنت وجربي مرة ثانية."
            m.contains("Private video", true) -> "هذا الفيديو خاص وما ينفع يتحمّل."
            m.contains("Unsupported URL", true) -> "هذا الرابط غير مدعوم."
            m.contains("is not available", true) || m.contains("unavailable", true) ->
                "الفيديو غير متوفر."
            else -> {
                val line = m.lines().firstOrNull { it.contains("ERROR", true) } ?: m.lines().firstOrNull() ?: m
                "صار خطأ: " + line.take(160)
            }
        }
    }

    private fun mime(name: String, audio: Boolean): String = when (name.substringAfterLast('.').lowercase()) {
        "mp4" -> if (audio) "audio/mp4" else "video/mp4"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "webm" -> if (audio) "audio/webm" else "video/webm"
        "mkv" -> "video/x-matroska"
        "opus", "ogg" -> "audio/ogg"
        else -> if (audio) "audio/*" else "video/*"
    }

    private fun saveToGallery(c: Context, f: File, audio: Boolean): Uri? {
        val cr = c.contentResolver
        val collection = if (audio)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime(f.name, audio))
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/Noor")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(collection, values) ?: return null
        cr.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        cr.update(uri, values, null, null)
        return uri
    }
}
