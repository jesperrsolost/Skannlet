package com.jrs.skannlet.data.repository

import com.jrs.skannlet.data.model.StoredAppState
import com.jrs.skannlet.data.storage.StoredData
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataBackupDispatchingTest {
    @Test
    fun `backup build and parse use the injected dispatcher`() {
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "backup-default-worker")
        }.asCoroutineDispatcher()
        val codec = RecordingBackupCodec()

        try {
            val bytes = runBlocking {
                buildBackupOnDispatcher(
                    codec = codec,
                    data = emptyData(),
                    appVersion = "1.1.2",
                    createdAt = 100L,
                    dispatcher = dispatcher,
                )
            }
            val restored = runBlocking {
                parseBackupOnDispatcher(codec, bytes, dispatcher)
            }

            assertEquals(emptyData(), restored)
            assertTrue(codec.buildThread.startsWith("backup-default-worker"))
            assertTrue(codec.parseThread.startsWith("backup-default-worker"))
        } finally {
            dispatcher.close()
        }
    }

    private class RecordingBackupCodec : AppDataBackupCodec {
        lateinit var buildThread: String
        lateinit var parseThread: String

        override fun build(data: StoredData, appVersion: String, createdAt: Long): ByteArray {
            buildThread = Thread.currentThread().name
            return byteArrayOf(1, 2, 3)
        }

        override fun parse(bytes: ByteArray): StoredData {
            parseThread = Thread.currentThread().name
            return emptyData()
        }
    }

    private companion object {
        fun emptyData() = StoredData(
            users = emptyList(),
            collections = emptyList(),
            rows = emptyList(),
            products = emptyList(),
            appState = StoredAppState(),
        )
    }
}
