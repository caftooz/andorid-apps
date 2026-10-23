package ru.diary.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.content.edit
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Данные о доступной версии. */
data class UpdateInfo(val version: String, val url: String, val size: Long, val sha256: String?, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Обновление приложения из GitHub Releases.
 *
 * Единственное сетевое обращение приложения: запрос последнего релиза и скачивание APK-файла (только https, только
 * хосты GitHub). Перед установкой проверяются имя пакета, версия, подпись (должна совпасть с установленной) и, если GitHub
 * отдал, SHA-256. Установку подтверждает система Android; полностью тихо ставить APK без системных прав нельзя.
 *
 * @param assetName имя APK в релизе, например `app-training-release.apk`.
 */
class Updater(
    context: Context,
    private val owner: String,
    private val repo: String,
    private val assetName: String,
) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Автоматическая проверка при запуске (включена по умолчанию, отключается в настройках). */
    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(v) { prefs.edit { putBoolean(KEY_AUTO, v) } }

    val installedVersion: String
        get() = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()

    /**
     * Автоматический режим при запуске: не чаще раза в [MIN_INTERVAL_MS] проверяет новую версию
     * и сразу скачивает её. Установка остаётся за пользователем.
     */
    suspend fun autoRun() {
        if (!autoCheck) return
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST, 0L) < MIN_INTERVAL_MS) {
            // Ранее скачанный, но не установленный файл остаётся доступным.
            restoreReady()
            return
        }
        check()
        (_state.value as? UpdateState.Available)?.let { download() }
    }

    /** Проверка наличия новой версии. */
    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.edit { putLong(KEY_LAST, System.currentTimeMillis()) }
            if (info != null && isNewer(info.version, installedVersion)) UpdateState.Available(info) else UpdateState.UpToDate(System.currentTimeMillis())
        } catch (e: NoReleases) {
            UpdateState.UpToDate(System.currentTimeMillis())
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось проверить обновления: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Скачивание найденной версии и проверка файла. */
    suspend fun download() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> return
            else -> return
        }
        _state.value = UpdateState.Downloading(info, 0)
        _state.value = try {
            val file = withContext(Dispatchers.IO) { fetchApk(info) }
            UpdateState.Ready(info, file)
        } catch (e: Exception) {
            UpdateState.Failed("Не удалось скачать обновление: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /**
     * Запускает установку. Возвращает `false`, если сначала нужно разрешить установку из этого приложения:
     * тогда открывается системный экран настройки, после возврата нужно нажать «Установить» ещё раз.
     */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return true
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${ctx.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                ready.file.inputStream().use { input ->
                    session.openWrite("update", 0, ready.file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION).setPackage(ctx.packageName)
                val pi = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pi.intentSender)
            }
            _state.value = UpdateState.Installing(ready.info)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Не удалось начать установку: " + (e.message ?: e.javaClass.simpleName))
        }
        return true
    }

    /** Сбрасывает сообщение об ошибке и возвращает состояние покоя. */
    fun dismissError() { if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle }

    // ---------- сеть ----------

    private class NoReleases : Exception("релизов пока нет")

    private fun fetchLatest(): UpdateInfo? {
        val conn = open("https://api.github.com/repos/$owner/$repo/releases/latest")
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            when (conn.responseCode) {
                200 -> Unit
                404 -> throw NoReleases()
                else -> error("GitHub ответил ${conn.responseCode}")
            }
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val o = json.parseToJsonElement(body).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content ?: return null
            val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content == assetName } ?: return null
            val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
            val digest = asset["digest"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content?.removePrefix("sha256:")
            return UpdateInfo(
                version = tag.removePrefix("v"), url = url, size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                sha256 = digest?.lowercase(), notes = o["body"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content.orEmpty().take(1000),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun updatesDir(): File = File(ctx.cacheDir, "updates").apply { mkdirs() }

    private fun fetchApk(info: UpdateInfo): File {
        val dir = updatesDir()
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, "$assetName.part")
        var url = info.url
        var conn: HttpURLConnection? = null
        try {
            for (i in 0..MAX_REDIRECTS) {
                conn?.disconnect()
                conn = open(url)
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), conn.getHeaderField("Location") ?: error("пустое перенаправление")).toString()
                    continue
                }
                if (code != 200) error("сервер ответил $code")
                break
            }
            val c = conn ?: error("нет соединения")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            require(total <= MAX_APK_BYTES) { "файл слишком большой" }
            val md = MessageDigest.getInstance("SHA-256")
            var read = 0L
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        read += n
                        require(read <= MAX_APK_BYTES) { "файл слишком большой" }
                        md.update(buf, 0, n); out.write(buf, 0, n)
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; _state.value = UpdateState.Downloading(info, pct) }
                        }
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && info.sha256 != hex) { part.delete(); error("контрольная сумма не совпала") }
            val apk = File(dir, assetName)
            part.renameTo(apk)
            verify(apk)
            return apk
        } finally {
            conn?.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val u = URL(url)
        require(u.protocol == "https") { "разрешён только https" }
        require(ALLOWED_HOSTS.any { u.host == it || u.host.endsWith(".$it") }) { "недопустимый адрес: ${u.host}" }
        return (u.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "ru.diary-updater")
        }
    }

    /** Проверка скачанного файла: тот же пакет, версия выше установленной, подпись совпадает с установленной. */
    private fun verify(apk: File) {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("файл не является APK")
        require(archive.packageName == ctx.packageName) { "в файле другое приложение" }
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        require(longVersion(archive) > longVersion(installed)) { "версия в файле не выше установленной" }
        val a = signers(archive); val b = signers(installed)
        require(a.isNotEmpty() && a == b) { "подпись обновления не совпадает с установленным приложением" }
    }

    @Suppress("DEPRECATION")
    private fun longVersion(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return sigs?.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }?.toSet().orEmpty()
    }

    private fun restoreReady() {
        val apk = File(updatesDir(), assetName)
        if (_state.value is UpdateState.Idle && apk.exists()) {
            val archive = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            val v = archive?.versionName
            if (v != null && isNewer(v, installedVersion)) {
                runCatching { verify(apk) }.onSuccess { _state.value = UpdateState.Ready(UpdateInfo(v, "", apk.length(), null, ""), apk) }
            }
        }
    }

    companion object {
        private const val KEY_AUTO = "auto"
        private const val KEY_LAST = "last_check"
        const val MIN_INTERVAL_MS = 12L * 60 * 60 * 1000
        private const val MAX_REDIRECTS = 5
        private const val MAX_APK_BYTES = 120L * 1024 * 1024
        private val ALLOWED_HOSTS = listOf("github.com", "githubusercontent.com")

        internal val results = MutableSharedFlow<String>(extraBufferCapacity = 4)

        /** Сообщения об итоге установки (ошибки), которые приходят из системы асинхронно. */
        val installErrors: SharedFlow<String> = results.asSharedFlow()

        /** `true`, если версия `a` новее `b`. Сравниваются числовые части через точку: `0.10.0` новее `0.9.9`. */
        fun isNewer(a: String, b: String): Boolean {
            fun parts(s: String) = s.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val x = parts(a); val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}

/** Принимает от системы ход установки: запускает экран подтверждения или сообщает об ошибке. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Updater.results.tryEmit("Установка не удалась (код $status): " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        }
    }

    companion object { const val ACTION = "ru.diary.core.update.INSTALL_RESULT" }
}
