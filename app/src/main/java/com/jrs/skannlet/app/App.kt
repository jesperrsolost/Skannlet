package com.jrs.skannlet.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jrs.skannlet.R
import com.jrs.skannlet.ui.components.NameInputDialog
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OmfScannerApp(
    viewModel: AppViewModel = viewModel(
        factory = AppViewModel.Factory(LocalContext.current.applicationContext),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    LaunchedEffect(context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AppEffect.ShareCollectionExport -> shareCollectionExport(context, effect)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val selectedRoute = selectedTopLevelRoute(backStackEntry?.destination?.route)
            NavigationBar {
                TopLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination.route == selectedRoute,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconResId),
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(innerPadding))
        } else {
            AppNavGraph(
                navController = navController,
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (!uiState.isLoading && uiState.needsUser) {
        NameInputDialog(
            title = "Første oppstart",
            label = "Navn",
            confirmText = "Lagre bruker",
            onConfirm = viewModel::addUser,
        )
    }
}

private suspend fun shareCollectionExport(
    context: Context,
    effect: AppEffect.ShareCollectionExport,
) {
    val exportUris = arrayListOf(effect.csvUri)
    val shareType = try {
        exportUris += withContext(Dispatchers.IO) {
            createPdfFromDocument(
                context = context,
                document = effect.printDocument,
                fileName = effect.printFileName,
            )
        }
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
        putExtra(Intent.EXTRA_TITLE, effect.csvFileName)
        putExtra(Intent.EXTRA_EMAIL, arrayOf("prosjektservice@omfjeld.no"))
        putExtra(Intent.EXTRA_SUBJECT, effect.csvFileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Del eksport"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Ingen app kan dele eksporten.", Toast.LENGTH_SHORT).show()
    }
}

private fun createPdfFromDocument(
    context: Context,
    document: CollectionPrintDocument,
    fileName: String,
): Uri {
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

    return FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.fileprovider",
        pdfFile,
    )
}

private fun writeCollectionPdf(
    document: CollectionPrintDocument,
    logo: Bitmap?,
    pdfFile: File,
) {
    val pdfDocument = PdfDocument()
    try {
        var pageNumber = 1
        var page = pdfDocument.startCollectionPage(pageNumber)
        var canvas = page.canvas
        var y = drawDocumentHeader(canvas, document, logo)
        y = drawTableHeader(canvas, y)

        document.rows.forEach { row ->
            val rowHeight = row.heightForPdf()
            if (y + rowHeight > A4_PAGE_HEIGHT - PAGE_MARGIN) {
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startCollectionPage(pageNumber)
                canvas = page.canvas
                y = drawTableHeader(canvas, PAGE_MARGIN)
            }
            drawRow(canvas, row, y, rowHeight)
            y += rowHeight
        }
        pdfDocument.finishPage(page)

        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
    } finally {
        pdfDocument.close()
    }
}

private fun PdfDocument.startCollectionPage(pageNumber: Int): PdfDocument.Page {
    val pageInfo = PdfDocument.PageInfo.Builder(A4_PAGE_WIDTH, A4_PAGE_HEIGHT, pageNumber).create()
    val page = startPage(pageInfo)
    page.canvas.drawColor(Color.WHITE)
    return page
}

private fun drawDocumentHeader(
    canvas: android.graphics.Canvas,
    document: CollectionPrintDocument,
    logo: Bitmap?,
): Float {
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 27, 31)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(85, 88, 98)
        textSize = 13f
    }

    canvas.drawText(document.title, PAGE_MARGIN, PAGE_MARGIN + 24f, titlePaint)
    logo?.let {
        val maxLogoWidth = 180f
        val maxLogoHeight = 48f
        val scale = minOf(maxLogoWidth / it.width, maxLogoHeight / it.height)
        val logoWidth = it.width * scale
        val logoHeight = it.height * scale
        val left = A4_PAGE_WIDTH - PAGE_MARGIN - logoWidth
        canvas.drawBitmap(
            it,
            null,
            RectF(left, PAGE_MARGIN, left + logoWidth, PAGE_MARGIN + logoHeight),
            Paint(Paint.ANTI_ALIAS_FLAG),
        )
    }
    canvas.drawText(document.metaText, PAGE_MARGIN, PAGE_MARGIN + 58f, metaPaint)
    return PAGE_MARGIN + 86f
}

private fun drawTableHeader(
    canvas: android.graphics.Canvas,
    y: Float,
): Float {
    val headerBackgroundPaint = Paint().apply {
        color = Color.rgb(240, 241, 247)
        style = Paint.Style.FILL
    }
    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 27, 31)
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawRect(PAGE_MARGIN, y, A4_PAGE_WIDTH - PAGE_MARGIN, y + TABLE_HEADER_HEIGHT, headerBackgroundPaint)
    canvas.drawText("Antall", COL_QTY_RIGHT - CELL_PADDING - headerPaint.measureText("Antall"), y + 21f, headerPaint)
    canvas.drawText("Strekkode", COL_BARCODE_LEFT + CELL_PADDING, y + 21f, headerPaint)
    canvas.drawText("Produkt", COL_PRODUCT_LEFT + CELL_PADDING, y + 21f, headerPaint)
    canvas.drawText("Opprettet", COL_CREATED_LEFT + CELL_PADDING, y + 21f, headerPaint)
    return y + TABLE_HEADER_HEIGHT
}

private fun drawRow(
    canvas: android.graphics.Canvas,
    row: CollectionPrintRow,
    y: Float,
    rowHeight: Float,
) {
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 27, 31)
        textSize = 11f
    }
    val dividerPaint = Paint().apply {
        color = Color.rgb(215, 216, 223)
        strokeWidth = 1f
    }
    val quantityPaint = Paint(bodyPaint).apply {
        textAlign = Paint.Align.RIGHT
    }
    val productLines = row.productName.wrapForPdf(bodyPaint, COL_CREATED_LEFT - COL_PRODUCT_LEFT - CELL_PADDING * 2)
    val baseline = y + 20f

    canvas.drawText(row.quantity, COL_QTY_RIGHT - CELL_PADDING, baseline, quantityPaint)
    canvas.drawText(row.barcode, COL_BARCODE_LEFT + CELL_PADDING, baseline, bodyPaint)
    productLines.forEachIndexed { index, line ->
        canvas.drawText(line, COL_PRODUCT_LEFT + CELL_PADDING, baseline + index * ROW_LINE_HEIGHT, bodyPaint)
    }
    canvas.drawText(row.createdAt, COL_CREATED_LEFT + CELL_PADDING, baseline, bodyPaint)
    canvas.drawLine(PAGE_MARGIN, y + rowHeight, A4_PAGE_WIDTH - PAGE_MARGIN, y + rowHeight, dividerPaint)
}

private fun CollectionPrintRow.heightForPdf(): Float {
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
    val productLines = productName.wrapForPdf(bodyPaint, COL_CREATED_LEFT - COL_PRODUCT_LEFT - CELL_PADDING * 2)
    return maxOf(TABLE_ROW_MIN_HEIGHT, 18f + productLines.size * ROW_LINE_HEIGHT)
}

private fun String.wrapForPdf(
    paint: Paint,
    maxWidth: Float,
): List<String> {
    val lines = mutableListOf<String>()
    var remaining = trim()
    while (remaining.isNotEmpty()) {
        var count = paint.breakText(remaining, true, maxWidth, null)
        if (count <= 0) break
        if (count < remaining.length) {
            val wordBreak = remaining.lastIndexOf(' ', count - 1)
            if (wordBreak > 0) count = wordBreak
        }
        lines += remaining.take(count).trim()
        remaining = remaining.drop(count).trimStart()
    }
    return lines.ifEmpty { listOf("") }
}

private const val A4_PAGE_WIDTH = 595
private const val A4_PAGE_HEIGHT = 842
private const val PAGE_MARGIN = 32f
private const val TABLE_HEADER_HEIGHT = 32f
private const val TABLE_ROW_MIN_HEIGHT = 34f
private const val ROW_LINE_HEIGHT = 14f
private const val CELL_PADDING = 8f
private const val COL_QTY_RIGHT = PAGE_MARGIN + 64f
private const val COL_BARCODE_LEFT = COL_QTY_RIGHT
private const val COL_PRODUCT_LEFT = COL_BARCODE_LEFT + 135f
private const val COL_CREATED_LEFT = A4_PAGE_WIDTH - PAGE_MARGIN - 135f
