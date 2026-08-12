package com.jrs.skannlet.printer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPrinterManagerTest {
    @Test
    fun missingConfigurationFailsBeforeNetworkAccess() = runBlocking {
        val transport = RecordingTransport()
        val manager = managerWith(
            settings = LabelPrinterSettings(),
            transport = transport,
        )

        val result = manager.printLabel(LabelPrintData("123456"))

        assertTrue(result.exceptionOrNull() is PrinterNotConfiguredException)
        assertEquals(0, transport.queryCount)
        assertEquals(0, transport.modelQueryCount)
        assertEquals(0, transport.sendCount)
    }

    @Test
    fun nonReadyPrinterDoesNotReceivePrintJob() = runBlocking {
        val transport = RecordingTransport(status = TscPrinterStatus.OutOfPaper)
        val manager = managerWith(configuredSettings(), transport)

        val result = manager.printLabel(LabelPrintData("123456"))

        assertTrue(result.exceptionOrNull() is PrinterNotReadyException)
        assertEquals("Skriveren er ikke klar: tom for etiketter.", result.exceptionOrNull()?.message)
        assertEquals(1, transport.queryCount)
        assertEquals(0, transport.modelQueryCount)
        assertEquals(0, transport.sendCount)
    }

    @Test
    fun printingPrinterDoesNotQueueAnotherJob() = runBlocking {
        val transport = RecordingTransport(status = TscPrinterStatus.Printing)
        val manager = managerWith(configuredSettings(), transport)

        val result = manager.printLabel(LabelPrintData("123456"))

        assertTrue(result.exceptionOrNull() is PrinterNotReadyException)
        assertEquals(0, transport.sendCount)
    }

    @Test
    fun readyPrinterReceivesRenderedSelectedFormatOnce() = runBlocking {
        val customFormat = SmallBarcodeLabelFormat.copy(
            id = "warehouse",
            name = "Warehouse",
            widthMm = 60.0,
        )
        val settings = configuredSettings().copy(
            selectedFormatId = customFormat.id,
            customFormats = listOf(customFormat),
        )
        val transport = RecordingTransport(status = TscPrinterStatus.Ready)
        val manager = managerWith(settings, transport)

        val result = manager.printLabel(LabelPrintData("7038010000017"))

        result.getOrThrow()
        assertEquals(1, transport.queryCount)
        assertEquals(0, transport.modelQueryCount)
        assertEquals(1, transport.sendCount)
        assertEquals(PrinterEndpoint("192.168.1.42", 9100), transport.queriedEndpoint)
        assertEquals(transport.queriedEndpoint, transport.sentEndpoint)
        assertArrayEquals(
            TsplLabelRenderer.render(customFormat, LabelPrintData("7038010000017")),
            transport.sentPayload,
        )
    }

    @Test
    fun transportFailureIsReturnedToCaller() = runBlocking {
        val failure = IllegalStateException("network failure")
        val transport = RecordingTransport(queryFailure = failure)
        val manager = managerWith(configuredSettings(), transport)

        val result = manager.printLabel(LabelPrintData("123456"))

        assertEquals(failure, result.exceptionOrNull())
        assertEquals(1, transport.queryCount)
        assertEquals(0, transport.sendCount)
    }

    @Test
    fun connectionTestVerifiesTc200ModelWhenReady() = runBlocking {
        val transport = RecordingTransport(model = "TC200 A1.81 EZ")
        val manager = managerWith(configuredSettings(), transport)

        val result = manager.testConnection(PrinterEndpoint("192.168.1.42", 9100)).getOrThrow()

        assertEquals(TscPrinterStatus.Ready, result.status)
        assertEquals("TC200 A1.81 EZ", result.model)
        assertEquals(1, transport.queryCount)
        assertEquals(1, transport.modelQueryCount)
    }

    @Test
    fun connectionTestRejectsDifferentPrinterModel() = runBlocking {
        listOf("TE200", "TC2000", "NOT-TC200").forEach { model ->
            val manager = managerWith(configuredSettings(), RecordingTransport(model = model))

            val result = manager.testConnection(PrinterEndpoint("192.168.1.42", 9100))

            assertTrue(result.exceptionOrNull() is UnexpectedPrinterModelException)
        }
    }

    @Test
    fun editingUnselectedFormatDoesNotChangeSelection() {
        val existing = SmallBarcodeLabelFormat.copy(id = "custom", name = "Custom")
        val manager = managerWith(
            configuredSettings().copy(customFormats = listOf(existing)),
            RecordingTransport(),
        )

        val updated = manager.saveCustomFormat(existing.copy(widthMm = 60.0))

        assertEquals(SMALL_BARCODE_FORMAT_ID, updated.selectedFormatId)
        assertEquals(60.0, updated.customFormats.single().widthMm, 0.0)
    }

    @Test
    fun savingNewFormatSelectsIt() {
        val manager = managerWith(configuredSettings(), RecordingTransport())
        val newFormat = SmallBarcodeLabelFormat.copy(id = "custom", name = "Custom")

        val updated = manager.saveCustomFormat(newFormat)

        assertEquals("custom", updated.selectedFormatId)
    }
}

private fun configuredSettings(): LabelPrinterSettings = LabelPrinterSettings(
    ipAddress = "192.168.1.42",
    port = 9100,
)

private fun managerWith(
    settings: LabelPrinterSettings,
    transport: RecordingTransport,
): LabelPrinterManager = LabelPrinterManager.createForTest(
    store = InMemorySettingsStore(settings),
    transport = transport,
)

private class InMemorySettingsStore(
    private var settings: LabelPrinterSettings,
) : LabelPrinterSettingsStore {
    override fun load(): LabelPrinterSettings = settings

    override fun save(settings: LabelPrinterSettings) {
        this.settings = settings
    }
}

private class RecordingTransport(
    private val status: TscPrinterStatus = TscPrinterStatus.Ready,
    private val model: String = "TC200",
    private val queryFailure: Throwable? = null,
    private val sendFailure: Throwable? = null,
) : TscPrinterTransport {
    var queryCount: Int = 0
        private set
    var sendCount: Int = 0
        private set
    var modelQueryCount: Int = 0
        private set
    var queriedEndpoint: PrinterEndpoint? = null
        private set
    var sentEndpoint: PrinterEndpoint? = null
        private set
    var sentPayload: ByteArray? = null
        private set

    override suspend fun queryStatus(endpoint: PrinterEndpoint): Result<TscPrinterStatus> {
        queryCount++
        queriedEndpoint = endpoint
        return if (queryFailure != null) {
            Result.failure(queryFailure)
        } else {
            Result.success(status)
        }
    }

    override suspend fun queryModel(endpoint: PrinterEndpoint): Result<String> {
        modelQueryCount++
        return Result.success(model)
    }

    override suspend fun send(endpoint: PrinterEndpoint, payload: ByteArray): Result<Unit> {
        sendCount++
        sentEndpoint = endpoint
        sentPayload = payload.copyOf()
        return if (sendFailure != null) {
            Result.failure(sendFailure)
        } else {
            Result.success(Unit)
        }
    }
}
