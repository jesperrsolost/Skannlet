package com.jrs.skannlet.app

import android.content.Context
import android.print.PrintAttributes
import android.util.Base64
import com.jrs.skannlet.R

fun collectionPrintAttributes(): PrintAttributes =
    PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

fun Context.collectionPrintHtml(document: CollectionPrintDocument): String =
    document.toPrintHtml(
        logoDataUri = pngResourceDataUri(R.drawable.omflogo),
    )

private fun Context.pngResourceDataUri(resourceId: Int): String =
    resources.openRawResource(resourceId).use { inputStream ->
        "data:image/png;base64,${Base64.encodeToString(inputStream.readBytes(), Base64.NO_WRAP)}"
    }

private fun CollectionPrintDocument.toPrintHtml(
    logoDataUri: String,
): String {
    val rowsHtml = rows.joinToString(separator = "\n") { row ->
        """
        <tr>
            <td>${row.quantity.escapeHtml()}</td>
            <td></td>
            <td></td>
            <td>${row.barcode.escapeHtml()}</td>
            <td>${row.productName.escapeHtml()}</td>
            <td>${row.createdAt.escapeHtml()}</td>
            <td></td>
        </tr>
        """.trimIndent()
    }

    return """
        <!doctype html>
        <html lang="no">
        <head>
            <meta charset="utf-8">
            <title>${title.escapeHtml()}</title>
            <style>
                body {
                    color: #1b1b1f;
                    font-family: sans-serif;
                    margin: 32px;
                }
                .print-header {
                    align-items: flex-start;
                    display: flex;
                    gap: 24px;
                    justify-content: space-between;
                    margin-bottom: 8px;
                }
                h1 {
                    font-size: 24px;
                    margin: 0;
                }
                .logo {
                    height: 48px;
                    max-width: 180px;
                    object-fit: contain;
                }
                .meta {
                    color: #555862;
                    font-size: 13px;
                    margin-bottom: 24px;
                }
                table {
                    border-collapse: collapse;
                    width: 100%;
                }
                th,
                td {
                    border-bottom: 1px solid #d7d8df;
                    padding: 8px;
                    text-align: left;
                    vertical-align: top;
                }
                th {
                    background: #f0f1f7;
                    font-weight: 700;
                }
                td:nth-child(1),
                th:nth-child(1),
                td:nth-child(2),
                th:nth-child(2),
                td:nth-child(3),
                th:nth-child(3) {
                    text-align: right;
                    width: 64px;
                }
            </style>
        </head>
        <body>
            <div class="print-header">
                <h1>${title.escapeHtml()}</h1>
                <img class="logo" src="${logoDataUri.escapeHtml()}" alt="OM Fjeld logo">
            </div>
            <div class="meta">
                ${metaText.escapeHtml()}
            </div>
            <table>
                <thead>
                    <tr>
                        <th>Ant. levert</th>
                        <th>Ant. bestilt</th>
                        <th>Rest</th>
                        <th>Strekkode</th>
                        <th>Produkt</th>
                        <th>Opprettet</th>
                        <th>Kommentar</th>
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String = buildString(length) {
    this@escapeHtml.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
