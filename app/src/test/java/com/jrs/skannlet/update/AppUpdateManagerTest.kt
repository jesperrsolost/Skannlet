package com.jrs.skannlet.update

import android.content.Intent
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `download reports pending running paused and verifying phases`() = runBlocking {
        val release = release(assetSize = 4)
        val stateStore = FakeUpdateStateStore()
        val gateway = FakeDownloadGateway(
            snapshots = listOf(
                PlatformDownloadSnapshot(PlatformDownloadStatus.Pending, downloadedBytes = 0),
                PlatformDownloadSnapshot(PlatformDownloadStatus.Running, downloadedBytes = 2),
                PlatformDownloadSnapshot(
                    PlatformDownloadStatus.Paused,
                    downloadedBytes = 2,
                    reason = 3,
                ),
                PlatformDownloadSnapshot(PlatformDownloadStatus.Successful, downloadedBytes = 4),
            ),
        )
        val target = temporaryFolder.newFile(release.assetName)
        val fileStore = FakeUpdateFileStore(target, byteArrayOf(1, 2, 3, 4))
        var verifiedFile: File? = null
        val manager = manager(
            stateStore = stateStore,
            downloadGateway = gateway,
            fileStore = fileStore,
            apkVerifier = DownloadedApkVerifier { file, _ ->
                assertEquals(17L, stateStore.pendingDownload()?.downloadId)
                verifiedFile = file
            },
        )
        val progress = mutableListOf<UpdateDownloadProgress>()

        val result = manager.downloadAndVerify(release, progress::add)

        assertSame(target, result)
        assertSame(target, verifiedFile)
        assertEquals(
            listOf(
                UpdateDownloadPhase.Pending,
                UpdateDownloadPhase.Running,
                UpdateDownloadPhase.Paused,
                UpdateDownloadPhase.Verifying,
            ),
            progress.map(UpdateDownloadProgress::phase),
        )
        assertEquals(listOf(0, 50, 50, 100), progress.map(UpdateDownloadProgress::percent))
        assertEquals(3, progress[2].reason)
        assertNull(stateStore.pendingDownload())
    }

    @Test
    fun `cancel removes persisted download and its partial file`() = runBlocking {
        val release = release(assetSize = 4)
        val stateStore = FakeUpdateStateStore(
            pending = PendingUpdateDownload(downloadId = 91L, release = release),
        )
        val target = temporaryFolder.newFile(release.assetName).apply {
            writeBytes(byteArrayOf(1, 2))
        }
        val gateway = FakeDownloadGateway(emptyList())
        val manager = manager(
            stateStore = stateStore,
            downloadGateway = gateway,
            fileStore = FakeUpdateFileStore(target),
        )

        manager.cancelPendingDownload()

        assertEquals(listOf(91L), gateway.removedIds)
        assertFalse(target.exists())
        assertNull(stateStore.pendingDownload())
    }

    @Test
    fun `lifecycle cancellation preserves pending metadata for process resume`() = runBlocking {
        val release = release(assetSize = 4)
        val pending = PendingUpdateDownload(downloadId = 42L, release = release)
        val stateStore = FakeUpdateStateStore(pending = pending)
        val gateway = FakeDownloadGateway(
            snapshots = listOf(
                PlatformDownloadSnapshot(PlatformDownloadStatus.Pending, downloadedBytes = 0),
            ),
        )
        val manager = manager(
            stateStore = stateStore,
            downloadGateway = gateway,
            fileStore = FakeUpdateFileStore(temporaryFolder.newFile(release.assetName)),
            pollDelay = { throw CancellationException("ViewModel cleared") },
        )

        var cancellationPropagated = false
        try {
            manager.resumePendingDownload { }
        } catch (_: CancellationException) {
            cancellationPropagated = true
        }

        assertTrue(cancellationPropagated)
        assertEquals(pending, stateStore.pendingDownload())
        assertTrue(gateway.removedIds.isEmpty())
    }

    @Test
    fun `download platform file and verification work stays on injected IO dispatcher`() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "update-test-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val release = release(assetSize = 4)
            val calledThreads = mutableListOf<String>()
            val stateStore = FakeUpdateStateStore(onAccess = { calledThreads += Thread.currentThread().name })
            val gateway = FakeDownloadGateway(
                snapshots = listOf(
                    PlatformDownloadSnapshot(PlatformDownloadStatus.Successful, downloadedBytes = 4),
                ),
                onAccess = { calledThreads += Thread.currentThread().name },
            )
            val target = temporaryFolder.newFile(release.assetName)
            val fileStore = FakeUpdateFileStore(
                file = target,
                preparedBytes = byteArrayOf(1, 2, 3, 4),
                onAccess = { calledThreads += Thread.currentThread().name },
            )
            val manager = manager(
                stateStore = stateStore,
                downloadGateway = gateway,
                fileStore = fileStore,
                apkVerifier = DownloadedApkVerifier { _, _ ->
                    calledThreads += Thread.currentThread().name
                },
                ioDispatcher = dispatcher,
            )

            runBlocking {
                manager.downloadAndVerify(release) {
                    calledThreads += Thread.currentThread().name
                }
            }

            assertTrue(calledThreads.isNotEmpty())
            assertTrue(calledThreads.toString(), calledThreads.all { it.startsWith("update-test-io") })
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private fun manager(
        stateStore: UpdateStateStore = FakeUpdateStateStore(),
        downloadGateway: UpdateDownloadGateway = FakeDownloadGateway(emptyList()),
        fileStore: UpdateFileStore = FakeUpdateFileStore(temporaryFolder.newFile()),
        apkVerifier: DownloadedApkVerifier = DownloadedApkVerifier { _, _ -> },
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
        pollDelay: suspend () -> Unit = {},
    ): AppUpdateManager = AppUpdateManager(
        currentVersionName = "1.1.2",
        releaseSource = UpdateReleaseSource { error("Not used by download tests") },
        stateStore = stateStore,
        downloadGateway = downloadGateway,
        fileStore = fileStore,
        apkVerifier = apkVerifier,
        installerGateway = FakeUpdateInstallerGateway,
        ioDispatcher = ioDispatcher,
        pollDelay = pollDelay,
    )

    private fun release(assetSize: Long): UpdateRelease = UpdateRelease(
        tag = "v1.1.3",
        version = "1.1.3",
        title = "v1.1.3",
        notes = "",
        releaseUrl = "https://github.com/jesperrsolost/Skannlet/releases/tag/v1.1.3",
        assetName = "Skannlet_v1.1.3.apk",
        downloadUrl = "https://example.test/Skannlet_v1.1.3.apk",
        assetSize = assetSize,
        sha256 = "0".repeat(64),
    )
}

private class FakeUpdateStateStore(
    private var pending: PendingUpdateDownload? = null,
    private val onAccess: () -> Unit = {},
) : UpdateStateStore {
    private var lastCheck = 0L
    private var deferredTag: String? = null
    private var deferredUntil = 0L

    override fun lastCheckAttempt(): Long = onAccess().let { lastCheck }

    override fun setLastCheckAttempt(value: Long) {
        onAccess()
        lastCheck = value
    }

    override fun deferredTag(): String? = onAccess().let { deferredTag }

    override fun deferredUntil(): Long = onAccess().let { deferredUntil }

    override fun defer(tag: String, until: Long) {
        onAccess()
        deferredTag = tag
        deferredUntil = until
    }

    override fun pendingDownload(): PendingUpdateDownload? = onAccess().let { pending }

    override fun savePendingDownload(pending: PendingUpdateDownload) {
        onAccess()
        this.pending = pending
    }

    override fun clearPendingDownload() {
        onAccess()
        pending = null
    }
}

private class FakeDownloadGateway(
    private val snapshots: List<PlatformDownloadSnapshot>,
    private val onAccess: () -> Unit = {},
) : UpdateDownloadGateway {
    private var queryIndex = 0
    val removedIds = mutableListOf<Long>()

    override fun enqueue(release: UpdateRelease): Long {
        onAccess()
        return 17L
    }

    override fun query(downloadId: Long): PlatformDownloadSnapshot? {
        onAccess()
        if (snapshots.isEmpty()) return null
        return snapshots[queryIndex.coerceAtMost(snapshots.lastIndex)].also {
            queryIndex++
        }
    }

    override fun remove(downloadId: Long) {
        onAccess()
        removedIds += downloadId
    }
}

private class FakeUpdateFileStore(
    private val file: File,
    private val preparedBytes: ByteArray = byteArrayOf(),
    private val onAccess: () -> Unit = {},
) : UpdateFileStore {
    override fun prepare(release: UpdateRelease): File {
        onAccess()
        file.writeBytes(preparedBytes)
        return file
    }

    override fun fileFor(release: UpdateRelease): File {
        onAccess()
        return file
    }

    override fun delete(release: UpdateRelease) {
        onAccess()
        file.delete()
    }
}

private data object FakeUpdateInstallerGateway : UpdateInstallerGateway {
    override fun canRequestPackageInstalls(): Boolean = error("Not used")

    override fun unknownSourcesIntent(): Intent = error("Not used")

    override fun installIntent(file: File): Intent = error("Not used")
}
