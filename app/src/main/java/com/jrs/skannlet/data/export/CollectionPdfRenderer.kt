package com.jrs.skannlet.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.jrs.skannlet.R
import java.io.OutputStream
import java.util.concurrent.CancellationException

internal class CollectionPdfRenderer(context: Context) {
    private val resources = context.applicationContext.resources

    fun write(
        document: CollectionPrintDocument,
        outputStream: OutputStream,
        isCancelled: () -> Boolean = { false },
    ) {
        val logo = BitmapFactory.decodeResource(resources, R.drawable.omflogo)
        val pdfDocument = PdfDocument()
        var unfinishedPage: PdfDocument.Page? = null
        try {
            ensureNotCancelled(isCancelled)
            var pageNumber = 1
            var page = pdfDocument.startCollectionPage(pageNumber).also { unfinishedPage = it }
            var canvas = page.canvas
            var y = drawDocumentHeader(canvas, document, logo)
            y = drawTableHeader(canvas, y)

            document.rows.forEach { row ->
                ensureNotCancelled(isCancelled)
                val rowHeight = row.heightForPdf()
                if (y + rowHeight > PDF_PAGE_HEIGHT - PAGE_MARGIN) {
                    pdfDocument.finishPage(page)
                    unfinishedPage = null
                    pageNumber++
                    page = pdfDocument.startCollectionPage(pageNumber).also { unfinishedPage = it }
                    canvas = page.canvas
                    y = drawTableHeader(canvas, PAGE_MARGIN)
                }
                drawRow(canvas, row, y, rowHeight)
                y += rowHeight
            }
            pdfDocument.finishPage(page)
            unfinishedPage = null

            ensureNotCancelled(isCancelled)
            pdfDocument.writeTo(outputStream)
        } finally {
            unfinishedPage?.let { page ->
                runCatching { pdfDocument.finishPage(page) }
            }
            pdfDocument.close()
            logo?.recycle()
        }
    }
}

private fun ensureNotCancelled(isCancelled: () -> Boolean) {
    if (isCancelled()) throw CancellationException("PDF rendering was cancelled.")
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
        textSize = TITLE_TEXT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(85, 88, 98)
        textSize = META_TEXT_SIZE
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
    val titleBaseline = PAGE_MARGIN + TITLE_BASELINE_OFFSET
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

    drawCellText(canvas, row.quantity, bodyPaint, COL_QUANTITY_LEFT, y, COL_QUANTITY_WIDTH, alignRight = true)
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

private const val PDF_PAGE_WIDTH = 595
private const val PDF_PAGE_HEIGHT = 842
private const val PAGE_MARGIN = 32f
private const val CONTENT_WIDTH = PDF_PAGE_WIDTH - PAGE_MARGIN * 2
private const val MAX_LOGO_WIDTH = 180f
private const val MAX_LOGO_HEIGHT = 48f
private const val PDF_TEXT_SCALE = 0.8f
private const val TITLE_TEXT_SIZE = 24f * PDF_TEXT_SCALE
private const val META_TEXT_SIZE = 13f * PDF_TEXT_SCALE
private const val TABLE_TEXT_SIZE = 16f * PDF_TEXT_SCALE
private const val TITLE_BASELINE_OFFSET = 24f * PDF_TEXT_SCALE
private const val HEADER_TITLE_LOGO_GAP = 24f * PDF_TEXT_SCALE
private const val HEADER_TITLE_LINE_HEIGHT = 28f * PDF_TEXT_SCALE
private const val HEADER_TITLE_DESCENT = 6f * PDF_TEXT_SCALE
private const val HEADER_META_TOP_GAP = 10f * PDF_TEXT_SCALE
private const val HEADER_AFTER_META_GAP = 28f * PDF_TEXT_SCALE
private const val TABLE_HEADER_HEIGHT = 56f * PDF_TEXT_SCALE
private const val TABLE_ROW_MIN_HEIGHT = 36f * PDF_TEXT_SCALE
private const val ROW_LINE_HEIGHT = 20f * PDF_TEXT_SCALE
private const val ROW_TEXT_BASELINE = 16f * PDF_TEXT_SCALE
private const val CELL_PADDING = 8f * PDF_TEXT_SCALE
private const val COL_QUANTITY_WIDTH = 64f
private const val COL_BARCODE_WIDTH = 90f
private const val COL_PRODUCT_WIDTH = 191f
private const val COL_CREATED_WIDTH = 82f
private const val COL_COMMENT_WIDTH =
    CONTENT_WIDTH - COL_QUANTITY_WIDTH - COL_BARCODE_WIDTH - COL_PRODUCT_WIDTH - COL_CREATED_WIDTH
private const val COL_QUANTITY_LEFT = PAGE_MARGIN
private const val COL_BARCODE_LEFT = COL_QUANTITY_LEFT + COL_QUANTITY_WIDTH
private const val COL_PRODUCT_LEFT = COL_BARCODE_LEFT + COL_BARCODE_WIDTH
private const val COL_CREATED_LEFT = COL_PRODUCT_LEFT + COL_PRODUCT_WIDTH
private const val COL_COMMENT_LEFT = COL_CREATED_LEFT + COL_CREATED_WIDTH
private val PDF_COLUMNS = listOf(
    PdfColumn("Antall", COL_QUANTITY_LEFT, COL_QUANTITY_WIDTH, alignRight = true),
    PdfColumn("Strekkode", COL_BARCODE_LEFT, COL_BARCODE_WIDTH),
    PdfColumn("Produkt", COL_PRODUCT_LEFT, COL_PRODUCT_WIDTH),
    PdfColumn("Opprettet", COL_CREATED_LEFT, COL_CREATED_WIDTH),
    PdfColumn("Kommentar", COL_COMMENT_LEFT, COL_COMMENT_WIDTH),
)
