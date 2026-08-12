package com.jrs.skannlet.printer

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val PRINTER_TIMEOUT_MILLIS = 3_000
private const val MODEL_RESPONSE_IDLE_TIMEOUT_MILLIS = 500
private const val MAX_MODEL_RESPONSE_BYTES = 128
private val TSC_STATUS_QUERY = byteArrayOf(0x1B, 0x21, 0x3F)
private val TSC_MODEL_QUERY = "~!T\r\n".toByteArray(Charsets.US_ASCII)

internal interface TscPrinterTransport {
    suspend fun queryStatus(endpoint: PrinterEndpoint): Result<TscPrinterStatus>
    suspend fun queryModel(endpoint: PrinterEndpoint): Result<String>
    suspend fun send(endpoint: PrinterEndpoint, payload: ByteArray): Result<Unit>
}

internal class TscTcpClient(
    private val timeoutMillis: Int = PRINTER_TIMEOUT_MILLIS,
) : TscPrinterTransport {
    init {
        require(timeoutMillis > 0) { "Tidsavbruddet må være større enn 0 ms." }
    }

    override suspend fun queryStatus(endpoint: PrinterEndpoint): Result<TscPrinterStatus> = socketResult { socket ->
        socket.connect(InetSocketAddress(endpoint.address, endpoint.port), timeoutMillis)
        socket.soTimeout = timeoutMillis
        socket.getOutputStream().apply {
            write(TSC_STATUS_QUERY)
            flush()
        }
        val status = socket.getInputStream().read()
        if (status < 0) throw IOException("Skriveren lukket forbindelsen uten statussvar.")
        TscPrinterStatus.fromCode(status)
    }

    override suspend fun send(endpoint: PrinterEndpoint, payload: ByteArray): Result<Unit> = socketResult { socket ->
        socket.connect(InetSocketAddress(endpoint.address, endpoint.port), timeoutMillis)
        socket.getOutputStream().apply {
            write(payload)
            flush()
        }
    }

    override suspend fun queryModel(endpoint: PrinterEndpoint): Result<String> = socketResult { socket ->
        socket.connect(InetSocketAddress(endpoint.address, endpoint.port), timeoutMillis)
        socket.soTimeout = timeoutMillis
        socket.getOutputStream().apply {
            write(TSC_MODEL_QUERY)
            flush()
        }
        readModelResponse(
            socket = socket,
            idleTimeoutMillis = minOf(timeoutMillis, MODEL_RESPONSE_IDLE_TIMEOUT_MILLIS).coerceAtLeast(1),
        )
    }
}

private fun readModelResponse(socket: Socket, idleTimeoutMillis: Int): String {
    val response = ByteArrayOutputStream()
    val input = socket.getInputStream()
    while (response.size() < MAX_MODEL_RESPONSE_BYTES) {
        val nextByte = try {
            input.read()
        } catch (exception: SocketTimeoutException) {
            if (response.size() == 0) throw exception
            break
        }
        if (nextByte < 0) break
        if (nextByte == '\r'.code || nextByte == '\n'.code) {
            if (response.size() > 0) break
            continue
        }
        response.write(nextByte)
        socket.soTimeout = idleTimeoutMillis
    }
    val model = response.toByteArray()
        .toString(Charsets.US_ASCII)
        .trim { it.code <= 0x20 }
    if (model.isBlank()) throw IOException("Skriveren svarte ikke med et modellnavn.")
    return model
}

private suspend fun <T> socketResult(block: (Socket) -> T): Result<T> = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        val socket = Socket()
        continuation.invokeOnCancellation {
            runCatching(socket::close)
        }
        try {
            val value = socket.use(block)
            if (continuation.isActive) continuation.resume(Result.success(value))
        } catch (exception: Exception) {
            if (continuation.isActive) continuation.resume(Result.failure(exception))
        }
    }
}

internal class UnexpectedTscStatusException(status: Int) : IOException(
    "Ugyldig TSC-statusbyte: 0x${status.toString(16).padStart(2, '0').uppercase()}",
)
