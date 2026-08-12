package com.jrs.skannlet.data.export

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

fun collectionPrintAttributes(): PrintAttributes =
    PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

internal fun collectionPrintAdapter(
    context: Context,
    document: CollectionPrintDocument,
    documentName: String,
): PrintDocumentAdapter = CollectionPrintAdapter(
    context = context.applicationContext,
    document = document,
    documentName = documentName,
)

private class CollectionPrintAdapter(
    context: Context,
    private val document: CollectionPrintDocument,
    private val documentName: String,
) : PrintDocumentAdapter() {
    private val renderer = CollectionPdfRenderer(context)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        executor.execute {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { outputStream ->
                    renderer.write(
                        document = document,
                        outputStream = outputStream,
                        isCancelled = cancellationSignal::isCanceled,
                    )
                }
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (_: CancellationException) {
                callback.onWriteCancelled()
            } catch (_: Exception) {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFailed("PDF kunne ikke opprettes.")
                }
            }
        }
    }

    override fun onFinish() {
        executor.shutdownNow()
    }
}
