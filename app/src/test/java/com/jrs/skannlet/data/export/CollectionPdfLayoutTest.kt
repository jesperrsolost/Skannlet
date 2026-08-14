package com.jrs.skannlet.data.export

import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionPdfLayoutTest {
    @Test
    fun `wrapped comment lines increase PDF row height`() {
        val ordinaryHeight = pdfRowHeight(
            barcodeLineCount = 1,
            productLineCount = 1,
            createdLineCount = 1,
            commentLineCount = 1,
        )
        val commentHeight = pdfRowHeight(
            barcodeLineCount = 1,
            productLineCount = 1,
            createdLineCount = 1,
            commentLineCount = 4,
        )

        assertTrue(commentHeight > ordinaryHeight)
    }
}
