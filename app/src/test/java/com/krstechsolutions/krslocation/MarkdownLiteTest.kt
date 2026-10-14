package com.krstechsolutions.krslocation

import com.krstechsolutions.krslocation.update.MarkdownLite
import com.krstechsolutions.krslocation.update.MarkdownLite.BlockType
import com.krstechsolutions.krslocation.update.MarkdownLite.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {

    private val notes = """
        Download **KRS-Location-1.2.0.apk** below.
        <!-- hidden -->
        > **Note:** debug signed.

        ## What's Changed
        * Add Google Maps by @kawincodes in https://github.com/kawincodes/krs-location/pull/3
        - Fix `clipping` bug

        **Full Changelog**: [compare](https://github.com/kawincodes/krs-location/compare/v1.1.1...v1.2.0)
    """.trimIndent()

    @Test
    fun parsesBlockTypes() {
        val blocks = MarkdownLite.parse(notes)
        assertEquals(
            listOf(BlockType.PARAGRAPH, BlockType.QUOTE, BlockType.HEADING, BlockType.BULLET, BlockType.BULLET, BlockType.PARAGRAPH),
            blocks.map { it.type },
        )
        assertEquals(2, blocks[2].level)
    }

    @Test
    fun parsesInlineSpans() {
        val spans = MarkdownLite.inline("Download **file.apk** and `code` from [here](https://x.y/z) or https://github.com/a/b/pull/3")
        assertTrue(spans.contains(Span.Bold("file.apk")))
        assertTrue(spans.contains(Span.Code("code")))
        assertTrue(spans.contains(Span.Link("here", "https://x.y/z")))
        assertTrue(spans.any { it is Span.Link && it.url == "https://github.com/a/b/pull/3" && it.text == "github.com/a/b/pull/3" })
    }

    @Test
    fun stripsHtmlComments() {
        assertTrue(MarkdownLite.plainText(notes).contains("hidden").not())
    }

    @Test
    fun plainTextDropsMarkup() {
        assertEquals("Add x by me", MarkdownLite.plainText("* Add **x** by `me`"))
    }
}
