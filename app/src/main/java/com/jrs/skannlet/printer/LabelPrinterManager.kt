package com.jrs.skannlet.printer

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val tc200ModelResponse = Regex("(?i)^TC200(?:\\s+.*)?$")

interface LabelPrinterManagerContract {
    fun loadSettings(): LabelPrinterSettings
    fun saveEndpoint(endpoint: PrinterEndpoint): LabelPrinterSettings
    fun selectFormat(formatId: String): LabelPrinterSettings
    fun saveCustomFormat(format: LabelFormat): LabelPrinterSettings
    fun deleteCustomFormat(formatId: String): LabelPrinterSettings
    suspend fun testConnection(endpoint: PrinterEndpoint): Result<TscConnectionTestResult>
    suspend fun printLabel(data: LabelPrintData): Result<Unit>
}

class LabelPrinterManager private constructor(
    private val store: LabelPrinterSettingsStore,
    private val transport: TscPrinterTransport,
) : LabelPrinterManagerContract {
    private val operationMutex = Mutex()

    constructor(context: Context) : this(
        store = SharedPreferencesLabelPrinterSettingsStore(context),
        transport = TscTcpClient(),
    )

    override fun loadSettings(): LabelPrinterSettings = store.load()

    override fun saveEndpoint(endpoint: PrinterEndpoint): LabelPrinterSettings {
        requireEndpoint(endpoint)
        return store.load().copy(
            ipAddress = endpoint.address.trim(),
            port = endpoint.port,
        ).also(store::save)
    }

    override fun selectFormat(formatId: String): LabelPrinterSettings {
        val current = store.load()
        require(current.formats.any { it.id == formatId }) { "Etikettformatet finnes ikke." }
        return current.copy(selectedFormatId = formatId).also(store::save)
    }

    override fun saveCustomFormat(format: LabelFormat): LabelPrinterSettings {
        require(!format.isBuiltIn) { "Det innebygde formatet kan ikke endres." }
        validateLabelFormat(format)?.let { throw InvalidLabelFormatException(it) }
        val current = store.load()
        require(
            current.formats.none { it.id != format.id && it.name.equals(format.name.trim(), ignoreCase = true) },
        ) { "Et format med dette navnet finnes allerede." }
        val normalized = format.copy(name = format.name.trim())
        val isNewFormat = current.customFormats.none { it.id == normalized.id }
        val customFormats = if (isNewFormat) {
            current.customFormats + normalized
        } else {
            current.customFormats.map { existing ->
                if (existing.id == normalized.id) normalized else existing
            }
        }
        return current.copy(
            selectedFormatId = if (isNewFormat) normalized.id else current.selectedFormatId,
            customFormats = customFormats,
        ).also(store::save)
    }

    override fun deleteCustomFormat(formatId: String): LabelPrinterSettings {
        require(formatId != SMALL_BARCODE_FORMAT_ID) { "Det innebygde formatet kan ikke slettes." }
        val current = store.load()
        val customFormats = current.customFormats.filterNot { it.id == formatId }
        require(customFormats.size != current.customFormats.size) { "Etikettformatet finnes ikke." }
        return current.copy(
            selectedFormatId = if (current.selectedFormatId == formatId) {
                SMALL_BARCODE_FORMAT_ID
            } else {
                current.selectedFormatId
            },
            customFormats = customFormats,
        ).also(store::save)
    }

    override suspend fun testConnection(endpoint: PrinterEndpoint): Result<TscConnectionTestResult> = operationResult {
        requireEndpoint(endpoint)
        val status = transport.queryStatus(endpoint).getOrThrow()
        val model = if (status == TscPrinterStatus.Ready) {
            transport.queryModel(endpoint).getOrThrow().also { reportedModel ->
                if (!reportedModel.matches(tc200ModelResponse)) {
                    throw UnexpectedPrinterModelException(reportedModel)
                }
            }
        } else {
            null
        }
        TscConnectionTestResult(status = status, model = model)
    }

    override suspend fun printLabel(data: LabelPrintData): Result<Unit> = operationResult {
        val settings = store.load()
        val endpoint = PrinterEndpoint(settings.ipAddress, settings.port)
        requireEndpoint(endpoint)
        val status = transport.queryStatus(endpoint).getOrThrow()
        if (status != TscPrinterStatus.Ready) throw PrinterNotReadyException(status)
        val payload = TsplLabelRenderer.render(settings.selectedFormat, data)
        transport.send(endpoint, payload).getOrThrow()
    }

    private suspend fun <T> operationResult(block: suspend () -> T): Result<T> = operationMutex.withLock {
        try {
            Result.success(block())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun requireEndpoint(endpoint: PrinterEndpoint) {
        if (endpoint.address.isBlank()) throw PrinterNotConfiguredException()
        require(endpoint.port in 1..65535) { "Skriverporten må være mellom 1 og 65535." }
    }

    internal companion object {
        fun createForTest(
            store: LabelPrinterSettingsStore,
            transport: TscPrinterTransport,
        ): LabelPrinterManager = LabelPrinterManager(store, transport)
    }
}

class PrinterNotConfiguredException : IOException(
    "Konfigurer etikettskriveren under Profil før utskrift.",
)

class PrinterNotReadyException(status: TscPrinterStatus) : IOException(
    "Skriveren er ikke klar: ${status.description}.",
)

class UnexpectedPrinterModelException(model: String) : IOException(
    "Forventet TSC TC200, men skriveren rapporterte: $model.",
)
