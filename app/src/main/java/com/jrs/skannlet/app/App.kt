package com.jrs.skannlet.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
        exportUris += createPdfFromDocument(
            context = context,
            document = effect.printDocument,
            fileName = effect.printFileName,
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
            if (y + rowHeight > PDF_PAGE_HEIGHT - PAGE_MARGIN) {
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
    val pageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, pageNumber).create()
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

    val logoPlacement = logo?.let {
        val scale = minOf(MAX_LOGO_WIDTH / it.width, MAX_LOGO_HEIGHT / it.height)
        val logoWidth = it.width * scale
        val logoHeight = it.height * scale
        val left = PDF_PAGE_WIDTH - PAGE_MARGIN - logoWidth
        it to RectF(left, PAGE_MARGIN, left + logoWidth, PAGE_MARGIN + logoHeight)
    }
    val titleMaxWidth = logoPlacement?.let { (_, bounds) ->
        bounds.left - PAGE_MARGIN - HEADER_TITLE_LOGO_GAP
    } ?: CONTENT_WIDTH
    val titleBaseline = PAGE_MARGIN + 24f
    val titleLines = document.title.wrapForPdf(titlePaint, titleMaxWidth)

    titleLines.forEachIndexed { index, line ->
        canvas.drawText(line, PAGE_MARGIN, titleBaseline + index * HEADER_TITLE_LINE_HEIGHT, titlePaint)
    }
    logoPlacement?.let { (bitmap, bounds) ->
        canvas.drawBitmap(
            bitmap,
            null,
            bounds,
            Paint(Paint.ANTI_ALIAS_FLAG),
        )
    }

    val titleBottom = titleBaseline + (titleLines.size - 1) * HEADER_TITLE_LINE_HEIGHT + HEADER_TITLE_DESCENT
    val headerContentBottom = maxOf(titleBottom, logoPlacement?.second?.bottom ?: PAGE_MARGIN, PAGE_MARGIN + MAX_LOGO_HEIGHT)
    val metaBaseline = headerContentBottom + HEADER_META_TOP_GAP

    canvas.drawText(document.metaText, PAGE_MARGIN, metaBaseline, metaPaint)
    return metaBaseline + HEADER_AFTER_META_GAP
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
        textSize = TABLE_TEXT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    canvas.drawRect(PAGE_MARGIN, y, PDF_PAGE_WIDTH - PAGE_MARGIN, y + TABLE_HEADER_HEIGHT, headerBackgroundPaint)
    PDF_COLUMNS.forEach { column ->
        drawCellText(
            canvas = canvas,
            text = column.title,
            paint = headerPaint,
            left = column.left,
            top = y,
            width = column.width,
            alignRight = column.alignRight,
        )
    }
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
        textSize = TABLE_TEXT_SIZE
    }
    val dividerPaint = Paint().apply {
        color = Color.rgb(215, 216, 223)
        strokeWidth = 1f
    }

    drawCellText(canvas, row.quantity, bodyPaint, COL_DELIVERED_LEFT, y, COL_DELIVERED_WIDTH, alignRight = true)
    drawCellText(canvas, row.barcode, bodyPaint, COL_BARCODE_LEFT, y, COL_BARCODE_WIDTH)
    drawCellText(canvas, row.productName, bodyPaint, COL_PRODUCT_LEFT, y, COL_PRODUCT_WIDTH)
    drawCellText(canvas, row.createdAt, bodyPaint, COL_CREATED_LEFT, y, COL_CREATED_WIDTH)
    canvas.drawLine(PAGE_MARGIN, y + rowHeight, PDF_PAGE_WIDTH - PAGE_MARGIN, y + rowHeight, dividerPaint)
}

private fun drawCellText(
    canvas: android.graphics.Canvas,
    text: String,
    paint: Paint,
    left: Float,
    top: Float,
    width: Float,
    alignRight: Boolean = false,
) {
    val lines = text.wrapForPdf(paint, width - CELL_PADDING * 2)
    val x = if (alignRight) left + width - CELL_PADDING else left + CELL_PADDING
    val alignedPaint = if (alignRight) {
        Paint(paint).apply { textAlign = Paint.Align.RIGHT }
    } else {
        paint
    }

    lines.forEachIndexed { index, line ->
        canvas.drawText(line, x, top + CELL_PADDING + ROW_TEXT_BASELINE + index * ROW_LINE_HEIGHT, alignedPaint)
    }
}

private fun CollectionPrintRow.heightForPdf(): Float {
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TABLE_TEXT_SIZE }
    val barcodeLines = barcode.wrapForPdf(bodyPaint, COL_BARCODE_WIDTH - CELL_PADDING * 2)
    val productLines = productName.wrapForPdf(bodyPaint, COL_PRODUCT_WIDTH - CELL_PADDING * 2)
    val createdLines = createdAt.wrapForPdf(bodyPaint, COL_CREATED_WIDTH - CELL_PADDING * 2)
    val lineCount = maxOf(1, barcodeLines.size, productLines.size, createdLines.size)
    return maxOf(TABLE_ROW_MIN_HEIGHT, CELL_PADDING * 2 + lineCount * ROW_LINE_HEIGHT)
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

private data class PdfColumn(
    val title: String,
    val left: Float,
    val width: Float,
    val alignRight: Boolean = false,
)

private const val PDF_PAGE_WIDTH = 612
private const val PDF_PAGE_HEIGHT = 792
private const val PAGE_MARGIN = 32f
private const val CONTENT_WIDTH = PDF_PAGE_WIDTH - PAGE_MARGIN * 2
private const val MAX_LOGO_WIDTH = 180f
private const val MAX_LOGO_HEIGHT = 48f
private const val HEADER_TITLE_LOGO_GAP = 24f
private const val HEADER_TITLE_LINE_HEIGHT = 28f
private const val HEADER_TITLE_DESCENT = 6f
private const val HEADER_META_TOP_GAP = 10f
private const val HEADER_AFTER_META_GAP = 28f
private const val TABLE_TEXT_SIZE = 16f
private const val TABLE_HEADER_HEIGHT = 56f
private const val TABLE_ROW_MIN_HEIGHT = 36f
private const val ROW_LINE_HEIGHT = 20f
private const val ROW_TEXT_BASELINE = 16f
private const val CELL_PADDING = 8f
private const val COL_ORDERED_WIDTH = 64f
private const val COL_DELIVERED_WIDTH = 64f
private const val COL_REST_WIDTH = 48f
private const val COL_BARCODE_WIDTH = 90f
private const val COL_PRODUCT_WIDTH = 135f
private const val COL_CREATED_WIDTH = 82f
private const val COL_COMMENT_WIDTH =
    CONTENT_WIDTH - COL_ORDERED_WIDTH - COL_DELIVERED_WIDTH - COL_REST_WIDTH -
        COL_BARCODE_WIDTH - COL_PRODUCT_WIDTH - COL_CREATED_WIDTH
private const val COL_DELIVERED_LEFT = PAGE_MARGIN
private const val COL_ORDERED_LEFT = COL_DELIVERED_LEFT + COL_DELIVERED_WIDTH
private const val COL_REST_LEFT = COL_ORDERED_LEFT + COL_ORDERED_WIDTH
private const val COL_BARCODE_LEFT = COL_REST_LEFT + COL_REST_WIDTH
private const val COL_PRODUCT_LEFT = COL_BARCODE_LEFT + COL_BARCODE_WIDTH
private const val COL_CREATED_LEFT = COL_PRODUCT_LEFT + COL_PRODUCT_WIDTH
private const val COL_COMMENT_LEFT = COL_CREATED_LEFT + COL_CREATED_WIDTH
private val PDF_COLUMNS = listOf(
    PdfColumn("Ant. levert", COL_DELIVERED_LEFT, COL_DELIVERED_WIDTH, alignRight = true),
    PdfColumn("Ant. bestilt", COL_ORDERED_LEFT, COL_ORDERED_WIDTH, alignRight = true),
    PdfColumn("Rest", COL_REST_LEFT, COL_REST_WIDTH, alignRight = true),
    PdfColumn("Strekkode", COL_BARCODE_LEFT, COL_BARCODE_WIDTH),
    PdfColumn("Produkt", COL_PRODUCT_LEFT, COL_PRODUCT_WIDTH),
    PdfColumn("Opprettet", COL_CREATED_LEFT, COL_CREATED_WIDTH),
    PdfColumn("Kommentar", COL_COMMENT_LEFT, COL_COMMENT_WIDTH),
)
