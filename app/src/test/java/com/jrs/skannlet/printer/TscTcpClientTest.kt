package com.jrs.skannlet.printer

import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TscTcpClientTest {
    @Test
    fun parsesEveryDocumentedImmediateStatus() {
        TscPrinterStatus.entries.forEach { expected ->
            assertEquals(expected, TscPrinterStatus.fromCode(expected.code))
        }
    }

    @Test
    fun rejectsUndocumentedImmediateStatus() {
        listOf(0x06, 0x07, 0x0E, 0x0F, 0x40, 0xFF, -1, 0x100).forEach { code ->
            val exception = assertThrows(UnexpectedTscStatusException::class.java) {
                TscPrinterStatus.fromCode(code)
            }
            assertEquals(
                "Ugyldig TSC-statusbyte: 0x${code.toString(16).padStart(2, '0').uppercase()}",
                exception.message,
            )
        }
    }

    @Test(timeout = 10_000)
    fun queryStatusSendsImmediateQueryAndParsesResponse() = withLoopbackServer(
        serverAction = { connection ->
            val query = connection.getInputStream().readExactly(3)
            connection.getOutputStream().apply {
                write(TscPrinterStatus.PaperJamAndHeadOpen.code)
                flush()
            }
            query
        },
    ) { port, serverResult ->
        val result = runBlocking {
            TscTcpClient(timeoutMillis = 2_000).queryStatus(
                PrinterEndpoint("127.0.0.1", port),
            )
        }

        assertEquals(TscPrinterStatus.PaperJamAndHeadOpen, result.getOrThrow())
        assertArrayEquals(byteArrayOf(0x1B, 0x21, 0x3F), serverResult.await())
    }

    @Test(timeout = 10_000)
    fun queryStatusRejectsUndocumentedNetworkResponse() = withLoopbackServer(
        serverAction = { connection ->
            connection.getInputStream().readExactly(3)
            connection.getOutputStream().apply {
                write(0x40)
                flush()
            }
        },
    ) { port, serverResult ->
        val result = runBlocking {
            TscTcpClient(timeoutMillis = 2_000).queryStatus(
                PrinterEndpoint("127.0.0.1", port),
            )
        }

        serverResult.await()
        assertTrue(result.exceptionOrNull() is UnexpectedTscStatusException)
    }

    @Test(timeout = 10_000)
    fun sendWritesPayloadWithoutModification() {
        val payload = "SIZE 50 mm,25 mm\r\nCLS\r\nPRINT 1,1\r\n"
            .toByteArray(StandardCharsets.US_ASCII)

        withLoopbackServer(
            serverAction = { connection -> connection.getInputStream().readBytes() },
        ) { port, serverResult ->
            val result = runBlocking {
                TscTcpClient(timeoutMillis = 2_000).send(
                    endpoint = PrinterEndpoint("127.0.0.1", port),
                    payload = payload,
                )
            }

            result.getOrThrow()
            assertArrayEquals(payload, serverResult.await())
        }
    }

    @Test(timeout = 10_000)
    fun queryModelSendsTc200ModelCommandAndReadsResponse() = withLoopbackServer(
        serverAction = { connection ->
            val query = connection.getInputStream().readExactly(5)
            connection.getOutputStream().apply {
                write("TC200 A1.81 EZ\r\n".toByteArray(StandardCharsets.US_ASCII))
                flush()
            }
            query
        },
    ) { port, serverResult ->
        val result = runBlocking {
            TscTcpClient(timeoutMillis = 2_000).queryModel(
                PrinterEndpoint("127.0.0.1", port),
            )
        }

        assertEquals("TC200 A1.81 EZ", result.getOrThrow())
        assertArrayEquals("~!T\r\n".toByteArray(StandardCharsets.US_ASCII), serverResult.await())
    }

    @Test(timeout = 10_000)
    fun cancellationClosesSocketWaitingForStatus() {
        val queryReceived = CountDownLatch(1)
        withLoopbackServer(
            serverAction = { connection ->
                connection.getInputStream().readExactly(3)
                queryReceived.countDown()
                connection.getInputStream().read()
            },
        ) { port, serverResult ->
            runBlocking {
                val query = launch(Dispatchers.Default) {
                    TscTcpClient(timeoutMillis = 5_000).queryStatus(
                        PrinterEndpoint("127.0.0.1", port),
                    )
                }
                assertTrue(queryReceived.await(4, TimeUnit.SECONDS))
                query.cancelAndJoin()
            }

            assertEquals(-1, serverResult.await())
        }
    }
}

private fun <T> withLoopbackServer(
    serverAction: (Socket) -> T,
    clientAction: (port: Int, serverResult: Future<T>) -> Unit,
) {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tsc-loopback-printer").apply { isDaemon = true }
    }
    val server = ServerSocket(0).apply { soTimeout = 3_000 }
    val serverResult = executor.submit<T> {
        server.accept().use { connection ->
            connection.soTimeout = 3_000
            serverAction(connection)
        }
    }

    try {
        clientAction(server.localPort, serverResult)
    } finally {
        server.close()
        serverResult.cancel(true)
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }
}

private fun <T> Future<T>.await(): T = get(4, TimeUnit.SECONDS)

private fun java.io.InputStream.readExactly(size: Int): ByteArray = ByteArray(size).also { bytes ->
    var offset = 0
    while (offset < bytes.size) {
        val count = read(bytes, offset, bytes.size - offset)
        check(count >= 0) { "Client closed before sending $size bytes." }
        offset += count
    }
}
