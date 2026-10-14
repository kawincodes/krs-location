package com.krstechsolutions.krslocation.update

/**
 * A deliberately small Markdown parser for GitHub release notes: headings, bullets,
 * quotes, paragraphs, bold, inline code and links. Enough for generated changelogs.
 */
object MarkdownLite {

    enum class BlockType { HEADING, BULLET, QUOTE, PARAGRAPH }

    data class Block(val type: BlockType, val spans: List<Span>, val level: Int = 0)

    sealed class Span {
        data class Text(val text: String) : Span()
        data class Bold(val text: String) : Span()
        data class Code(val text: String) : Span()
        data class Link(val text: String, val url: String) : Span()
    }

    private val htmlComment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val bullet = Regex("""^\s*[-*+]\s+(.*)$""")
    private val quote = Regex("""^>\s?(.*)$""")
    private val inline = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)|(https?://[^\s<>)]+)""")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += Block(BlockType.PARAGRAPH, inline(paragraph.toString().trim()))
            paragraph.setLength(0)
        }

        htmlComment.replace(markdown, "").lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flushParagraph()
                heading.matches(line) -> {
                    flushParagraph()
                    val m = heading.find(line)!!
                    blocks += Block(BlockType.HEADING, inline(m.groupValues[2].trim()), m.groupValues[1].length)
                }
                bullet.matches(line) -> {
                    flushParagraph()
                    blocks += Block(BlockType.BULLET, inline(bullet.find(line)!!.groupValues[1].trim()))
                }
                quote.matches(line) -> {
                    flushParagraph()
                    blocks += Block(BlockType.QUOTE, inline(quote.find(line)!!.groupValues[1].trim()))
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }
        flushParagraph()
        return blocks
    }

    fun inline(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var last = 0
        for (m in inline.findAll(text)) {
            if (m.range.first > last) spans += Span.Text(text.substring(last, m.range.first))
            when {
                m.groups[1] != null -> spans += Span.Bold(m.groupValues[1])
                m.groups[2] != null -> spans += Span.Code(m.groupValues[2])
                m.groups[3] != null -> spans += Span.Link(m.groupValues[3], m.groupValues[4])
                m.groups[5] != null -> spans += Span.Link(shortUrl(m.groupValues[5]), m.groupValues[5])
            }
            last = m.range.last + 1
        }
        if (last < text.length) spans += Span.Text(text.substring(last))
        return spans
    }

    /** "github.com/owner/repo/pull/12" instead of the full https URL. */
    private fun shortUrl(url: String): String {
        val s = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        return if (s.length > 48) s.take(45) + "…" else s
    }

    fun plainText(markdown: String): String =
        parse(markdown).joinToString("\n") { block ->
            block.spans.joinToString("") {
                when (it) {
                    is Span.Text -> it.text
                    is Span.Bold -> it.text
                    is Span.Code -> it.text
                    is Span.Link -> it.text
                }
            }
        }
}
