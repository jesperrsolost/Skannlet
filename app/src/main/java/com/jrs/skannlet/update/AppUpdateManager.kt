package com.jrs.skannlet.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/jesperrsolost/Skannlet/releases/latest"
private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
private const val MAX_APK_BYTES = 100L * 1024 * 1024
private const val UPDATE_PREFERENCES = "app_updates"
private const val DOWNLOAD_POLL_INTERVAL_MS = 1_000L

@Serializable
data class UpdateRelease(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val assetName: String,
    val downloadUrl: String,
    val assetSize: Long,
    val sha256: String,
)

sealed interface UpdateCheckResult {
    data object Skipped : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Available(val release: UpdateRelease) : UpdateCheckResult
}

enum class UpdateDownloadPhase {
    Pending,
    Running,
    Paused,
    Verifying,
}

data class UpdateDownloadProgress(
    val phase: UpdateDownloadPhase,
    val percent: Int?,
    val reason: Int? = null,
)

interface AppUpdateManagerContract {
    suspend fun checkForUpdate(
        force: Boolean,
        now: Long = System.currentTimeMillis(),
    ): UpdateCheckResult

    fun defer(
        release: UpdateRelease,
        now: Long = System.currentTimeMillis(),
    )

    suspend fun downloadAndVerify(
        release: UpdateRelease,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File

    suspend fun resumePendingDownload(
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): Pair<UpdateRelease, File>?

    suspend fun cancelPendingDownload()

    fun pendingRelease(): UpdateRelease?

    fun canRequestPackageInstalls(): Boolean

    fun unknownSourcesIntent(): Intent

    fun installIntent(file: File): Intent
}

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(value: String): SemanticVersion? {
            val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value.trim()) ?: return null
            return runCatching {
                SemanticVersion(
                    major = match.groupValues[1].toInt(),
                    minor = match.groupValues[2].toInt(),
                    patch = match.groupValues[3].toInt(),
                )
            }.getOrNull()
        }
    }
}

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null,
    val size: Long,
    val digest: String? = null,
)

@Serializable
internal data class PendingUpdateDownload(
    val downloadId: Long,
    val release: UpdateRelease,
)

internal enum class PlatformDownloadStatus {
    Pending,
    Running,
    Paused,
    Successful,
    Failed,
}

internal data class PlatformDownloadSnapshot(
    val status: PlatformDownloadStatus,
    val downloadedBytes: Long,
    val reason: Int? = null,
)

internal fun interface UpdateReleaseSource {
    suspend fun fetchLatestRelease(): GitHubRelease
}

internal interface UpdateStateStore {
    fun lastCheckAttempt(): Long
    fun setLastCheckAttempt(value: Long)
    fun deferredTag(): String?
    fun deferredUntil(): Long
    fun defer(tag: String, until: Long)
    fun pendingDownload(): PendingUpdateDownload?
    fun savePendingDownload(pending: PendingUpdateDownload)
    fun clearPendingDownload()
}

internal interface UpdateDownloadGateway {
    fun enqueue(release: UpdateRelease): Long
    fun query(downloadId: Long): PlatformDownloadSnapshot?
    fun remove(downloadId: Long)
}

internal interface UpdateFileStore {
    fun prepare(release: UpdateRelease): File
    fun fileFor(release: UpdateRelease): File
    fun delete(release: UpdateRelease)
}

internal fun interface DownloadedApkVerifier {
    suspend fun verify(file: File, release: UpdateRelease)
}

internal interface UpdateInstallerGateway {
    fun canRequestPackageInstalls(): Boolean
    fun unknownSourcesIntent(): Intent
    fun installIntent(file: File): Intent
}

class AppUpdateManager internal constructor(
    private val currentVersionName: String,
    private val releaseSource: UpdateReleaseSource,
    private val stateStore: UpdateStateStore,
    private val downloadGateway: UpdateDownloadGateway,
    private val fileStore: UpdateFileStore,
    private val apkVerifier: DownloadedApkVerifier,
    private val installerGateway: UpdateInstallerGateway,
    private val ioDispatcher: CoroutineDispatcher,
    private val pollDelay: suspend () -> Unit,
) : AppUpdateManagerContract {
    constructor(
        context: Context,
        currentVersionName: String,
        currentVersionCode: Long,
    ) : this(
        currentVersionName = currentVersionName,
        releaseSource = GitHubUpdateReleaseSource(currentVersionName),
        stateStore = SharedPreferencesUpdateStateStore(
            preferences = context.applicationContext.getSharedPreferences(
                UPDATE_PREFERENCES,
                Context.MODE_PRIVATE,
            ),
        ),
        downloadGateway = AndroidUpdateDownloadGateway(context.applicationContext),
        fileStore = ExternalUpdateFileStore(context.applicationContext),
        apkVerifier = AndroidDownloadedApkVerifier(
            context = context.applicationContext,
            currentVersionCode = currentVersionCode,
        ),
        installerGateway = AndroidUpdateInstallerGateway(context.applicationContext),
        ioDispatcher = Dispatchers.IO,
        pollDelay = { delay(DOWNLOAD_POLL_INTERVAL_MS) },
    )

    override suspend fun checkForUpdate(force: Boolean, now: Long): UpdateCheckResult =
        withContext(ioDispatcher) {
            if (!force && now - stateStore.lastCheckAttempt() < CHECK_INTERVAL_MS) {
                return@withContext UpdateCheckResult.Skipped
            }
            if (!force) stateStore.setLastCheckAttempt(now)

            val release = releaseSource.fetchLatestRelease()
            require(!release.draft && !release.prerelease) { "Utgivelsen er ikke en stabil versjon." }
            val current = SemanticVersion.parse(currentVersionName)
                ?: error("Gjeldende appversjon er ugyldig.")
            val latest = SemanticVersion.parse(release.tagName)
                ?: error("GitHub-utgivelsen har et ugyldig versjonsnummer.")
            if (latest <= current) return@withContext UpdateCheckResult.UpToDate

            val expectedName = "Skannlet_v$latest.apk"
            val assets = release.assets.filter { asset ->
                asset.name == expectedName &&
                    asset.contentType == "application/vnd.android.package-archive"
            }
            require(assets.size == 1) { "Utgivelsen mangler en entydig APK-fil." }
            val asset = assets.single()
            require(asset.size in 1..MAX_APK_BYTES) { "APK-filen har ugyldig størrelse." }
            val digest = asset.digest
                ?.takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")
                ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
                ?: error("Utgivelsen mangler en gyldig SHA-256-kontrollsum.")
            val update = UpdateRelease(
                tag = release.tagName,
                version = latest.toString(),
                title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                notes = release.body.orEmpty(),
                releaseUrl = release.htmlUrl,
                assetName = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                assetSize = asset.size,
                sha256 = digest.lowercase(),
            )
            if (!force && stateStore.deferredTag() == update.tag && now < stateStore.deferredUntil()) {
                UpdateCheckResult.Skipped
            } else {
                UpdateCheckResult.Available(update)
            }
        }

    override fun defer(release: UpdateRelease, now: Long) {
        stateStore.defer(release.tag, now + CHECK_INTERVAL_MS)
    }

    override suspend fun downloadAndVerify(
        release: UpdateRelease,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File = withContext(ioDispatcher) {
        cancelPendingDownloadInternal()
        val target = fileStore.prepare(release)
        val downloadId = downloadGateway.enqueue(release)
        try {
            stateStore.savePendingDownload(PendingUpdateDownload(downloadId, release))
        } catch (exception: Exception) {
            downloadGateway.remove(downloadId)
            fileStore.delete(release)
            throw exception
        }

        awaitDownload(downloadId, release.assetSize, onProgress)
        onProgress(
            UpdateDownloadProgress(
                phase = UpdateDownloadPhase.Verifying,
                percent = 100,
            ),
        )
        require(target.isFile) { "Den nedlastede APK-filen finnes ikke." }
        apkVerifier.verify(target, release)
        stateStore.clearPendingDownload()
        target
    }

    override suspend fun resumePendingDownload(
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): Pair<UpdateRelease, File>? = withContext(ioDispatcher) {
        val pending = stateStore.pendingDownload() ?: return@withContext null
        try {
            awaitDownload(pending.downloadId, pending.release.assetSize, onProgress)
            onProgress(
                UpdateDownloadProgress(
                    phase = UpdateDownloadPhase.Verifying,
                    percent = 100,
                ),
            )
            val target = fileStore.fileFor(pending.release)
            require(target.isFile) { "Den nedlastede APK-filen finnes ikke." }
            apkVerifier.verify(target, pending.release)
            stateStore.clearPendingDownload()
            pending.release to target
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            runCatching { downloadGateway.remove(pending.downloadId) }
            runCatching { fileStore.delete(pending.release) }
            stateStore.clearPendingDownload()
            throw exception
        }
    }

    override suspend fun cancelPendingDownload() {
        withContext(ioDispatcher) {
            cancelPendingDownloadInternal()
        }
    }

    override fun pendingRelease(): UpdateRelease? = stateStore.pendingDownload()?.release

    override fun canRequestPackageInstalls(): Boolean = installerGateway.canRequestPackageInstalls()

    override fun unknownSourcesIntent(): Intent = installerGateway.unknownSourcesIntent()

    override fun installIntent(file: File): Intent = installerGateway.installIntent(file)

    private fun cancelPendingDownloadInternal() {
        val pending = stateStore.pendingDownload() ?: return
        downloadGateway.remove(pending.downloadId)
        fileStore.delete(pending.release)
        stateStore.clearPendingDownload()
    }

    private suspend fun awaitDownload(
        downloadId: Long,
        expectedSize: Long,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val snapshot = requireNotNull(downloadGateway.query(downloadId)) {
                "Nedlastingen finnes ikke lenger."
            }
            if (snapshot.downloadedBytes > expectedSize || snapshot.downloadedBytes > MAX_APK_BYTES) {
                downloadGateway.remove(downloadId)
                error("Nedlastingen er større enn forventet.")
            }
            val percent = if (expectedSize > 0 && snapshot.downloadedBytes >= 0) {
                ((snapshot.downloadedBytes * 100 / expectedSize).coerceIn(0, 100)).toInt()
            } else {
                null
            }
            when (snapshot.status) {
                PlatformDownloadStatus.Pending -> onProgress(
                    UpdateDownloadProgress(UpdateDownloadPhase.Pending, percent, snapshot.reason),
                )
                PlatformDownloadStatus.Running -> onProgress(
                    UpdateDownloadProgress(UpdateDownloadPhase.Running, percent, snapshot.reason),
                )
                PlatformDownloadStatus.Paused -> onProgress(
                    UpdateDownloadProgress(UpdateDownloadPhase.Paused, percent, snapshot.reason),
                )
                PlatformDownloadStatus.Successful -> return
                PlatformDownloadStatus.Failed -> error(
                    "Nedlastingen feilet (kode ${snapshot.reason ?: 0}).",
                )
            }
            pollDelay()
        }
    }
}

private class GitHubUpdateReleaseSource(
    private val currentVersionName: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateReleaseSource {
    override suspend fun fetchLatestRelease(): GitHubRelease {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Skannlet-Android/$currentVersionName")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub svarte med HTTP ${connection.responseCode}."
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return json.decodeFromString(response)
        } finally {
            connection.disconnect()
        }
    }
}

private class SharedPreferencesUpdateStateStore(
    private val preferences: SharedPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : UpdateStateStore {
    override fun lastCheckAttempt(): Long = preferences.getLong(KEY_LAST_CHECK_ATTEMPT, 0L)

    override fun setLastCheckAttempt(value: Long) {
        preferences.edit().putLong(KEY_LAST_CHECK_ATTEMPT, value).apply()
    }

    override fun deferredTag(): String? = preferences.getString(KEY_DEFERRED_TAG, null)

    override fun deferredUntil(): Long = preferences.getLong(KEY_DEFERRED_UNTIL, 0L)

    override fun defer(tag: String, until: Long) {
        preferences.edit()
            .putString(KEY_DEFERRED_TAG, tag)
            .putLong(KEY_DEFERRED_UNTIL, until)
            .apply()
    }

    override fun pendingDownload(): PendingUpdateDownload? {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val encodedRelease = preferences.getString(KEY_PENDING_RELEASE, null)
        if (downloadId < 0 || encodedRelease == null) return null
        val release = runCatching { json.decodeFromString<UpdateRelease>(encodedRelease) }.getOrNull()
            ?: return null
        return PendingUpdateDownload(downloadId, release)
    }

    override fun savePendingDownload(pending: PendingUpdateDownload) {
        check(
            preferences.edit()
                .putLong(KEY_DOWNLOAD_ID, pending.downloadId)
                .putString(KEY_PENDING_RELEASE, json.encodeToString(pending.release))
                .commit(),
        ) { "Nedlastingstilstanden kunne ikke lagres." }
    }

    override fun clearPendingDownload() {
        check(
            preferences.edit()
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_PENDING_RELEASE)
                .commit(),
        ) { "Nedlastingstilstanden kunne ikke slettes." }
    }

    private companion object {
        const val KEY_LAST_CHECK_ATTEMPT = "last_check_attempt"
        const val KEY_DEFERRED_TAG = "deferred_tag"
        const val KEY_DEFERRED_UNTIL = "deferred_until"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_PENDING_RELEASE = "pending_release"
    }
}

private class AndroidUpdateDownloadGateway(
    private val appContext: Context,
) : UpdateDownloadGateway {
    private val downloadManager = appContext.getSystemService(DownloadManager::class.java)

    override fun enqueue(release: UpdateRelease): Long {
        val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
            .setTitle("Skannlet ${release.version}")
            .setDescription("Laster ned appoppdatering")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                "updates/${release.assetName}",
            )
        return downloadManager.enqueue(request)
    }

    override fun query(downloadId: Long): PlatformDownloadSnapshot? {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING -> PlatformDownloadStatus.Pending
                DownloadManager.STATUS_RUNNING -> PlatformDownloadStatus.Running
                DownloadManager.STATUS_PAUSED -> PlatformDownloadStatus.Paused
                DownloadManager.STATUS_SUCCESSFUL -> PlatformDownloadStatus.Successful
                DownloadManager.STATUS_FAILED -> PlatformDownloadStatus.Failed
                else -> return null
            }
            return PlatformDownloadSnapshot(
                status = status,
                downloadedBytes = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                ),
                reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }

    override fun remove(downloadId: Long) {
        downloadManager.remove(downloadId)
    }
}

private class ExternalUpdateFileStore(
    private val appContext: Context,
) : UpdateFileStore {
    override fun prepare(release: UpdateRelease): File = fileFor(release).apply {
        val directory = requireNotNull(parentFile) { "Oppdateringsmappen finnes ikke." }
        require(directory.exists() || directory.mkdirs()) { "Oppdateringsmappen kunne ikke opprettes." }
        require(!exists() || delete()) { "En eldre APK-fil kunne ikke slettes." }
    }

    override fun fileFor(release: UpdateRelease): File {
        val downloads = requireNotNull(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
            "Ekstern lagring er ikke tilgjengelig."
        }
        return File(downloads, "updates/${release.assetName}")
    }

    override fun delete(release: UpdateRelease) {
        val target = fileFor(release)
        require(!target.exists() || target.delete()) { "APK-filen kunne ikke slettes." }
    }
}

private class AndroidDownloadedApkVerifier(
    context: Context,
    private val currentVersionCode: Long,
) : DownloadedApkVerifier {
    private val appContext = context.applicationContext

    override suspend fun verify(file: File, release: UpdateRelease) {
        require(file.length() == release.assetSize) { "APK-filens størrelse stemmer ikke." }
        require(file.sha256(currentCoroutineContext()) == release.sha256) {
            "APK-filens kontrollsum stemmer ikke."
        }
        currentCoroutineContext().ensureActive()
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = requireNotNull(appContext.packageManager.getPackageArchiveInfo(file.absolutePath, flags)) {
            "APK-filen kunne ikke leses."
        }.also { info ->
            info.applicationInfo?.sourceDir = file.absolutePath
            info.applicationInfo?.publicSourceDir = file.absolutePath
        }
        require(archive.packageName == appContext.packageName) { "APK-filen har feil pakkenavn." }
        require(archive.versionName == release.version) { "APK-versjonen stemmer ikke med GitHub-utgivelsen." }
        require(archive.longVersionCode > currentVersionCode) { "APK-filen har ikke en nyere versjonskode." }

        val installed = appContext.packageManager.getPackageInfo(appContext.packageName, flags)
        require(archive.signingDigests() == installed.signingDigests()) { "APK-signaturen stemmer ikke." }
    }
}

private class AndroidUpdateInstallerGateway(
    private val appContext: Context,
) : UpdateInstallerGateway {
    override fun canRequestPackageInstalls(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    override fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${appContext.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}

private suspend fun File.sha256(coroutineContext: CoroutineContext): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(16 * 1024)
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun android.content.pm.PackageInfo.signingDigests(): Set<String> {
    return signingInfo?.apkContentsSigners.orEmpty().mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
