package com.jrs.skannlet.data.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jrs.skannlet.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun shareCollectionExport(
    context: Context,
    csvUri: Uri,
    csvFileName: String,
    printDocument: CollectionPrintDocument,
    printFileName: String,
) {
    val exportUris = arrayListOf(csvUri)
    val shareType = try {
        exportUris += createPdfFromDocument(
            context = context,
            document = printDocument,
            fileName = printFileName,
        )
        "*/*"
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        Toast.makeText(context, "PDF kunne ikke legges ved.", Toast.LENGTH_SHORT).show()
        "text/csv"
    }
    val intent = Intent(
        if (exportUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND,
    ).apply {
        type = shareType
        if (exportUris.size > 1) {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, exportUris)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "application/pdf"))
        } else {
            putExtra(Intent.EXTRA_STREAM, exportUris.first())
        }
        putExtra(Intent.EXTRA_TITLE, csvFileName)
        putExtra(Intent.EXTRA_EMAIL, arrayOf("prosjektservice@omfjeld.no"))
        putExtra(Intent.EXTRA_SUBJECT, csvFileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Del eksport"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Ingen app kan dele eksporten.", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun createPdfFromDocument(
    context: Context,
    document: CollectionPrintDocument,
    fileName: String,
): Uri = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
    val pdfFile = File(exportDir, fileName)
    val logo = BitmapFactory.decodeResource(appContext.resources, R.drawable.omflogo)

    try {
        writeCollectionPdf(
            document = document,
            logo = logo,
            pdfFile = pdfFile,
        )
    } finally {
        logo?.recycle()
    }

    FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.fileprovider",
        pdfFile,
    )
}
