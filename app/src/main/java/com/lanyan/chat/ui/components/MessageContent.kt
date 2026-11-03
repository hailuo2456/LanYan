package com.lanyan.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MessageContent(content: String, modifier: Modifier = Modifier) {
    val segments = remember(content) { parseCodeSegments(content) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is Segment.Text -> if (segment.value.isNotBlank()) {
                    MarkdownBlocks(segment.value)
                }
                is Segment.Code -> Surface(
                    color = Color(0xFF171B26),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        if (segment.language.isNotBlank()) {
                            Text(
                                text = segment.language,
                                color = Color(0xFF9DA7BA),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 14.dp, top = 10.dp),
                            )
                        }
                        SelectionContainer {
                            Text(
                                text = segment.value.trim(),
                                color = Color(0xFFF1F4FA),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlocks(value: String) {
    val blocks = remember(value) { parseMarkdownBlocks(value) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> Text(
                        text = inlineMarkdown(block.text),
                        fontSize = when (block.level) {
                            1 -> 24.sp
                            2 -> 21.sp
                            else -> 18.sp
                        },
                        lineHeight = when (block.level) {
                            1 -> 32.sp
                            2 -> 29.sp
                            else -> 26.sp
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (block.level == 1) 6.dp else 2.dp),
                    )

                    is MarkdownBlock.Paragraph -> Text(
                        text = inlineMarkdown(block.text),
                        fontSize = 17.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    is MarkdownBlock.Bullet -> Row {
                        Text(
                            text = "•  ",
                            fontSize = 17.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = inlineMarkdown(block.text),
                            fontSize = 17.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    is MarkdownBlock.Ordered -> Row {
                        Text(
                            text = "${block.number}. ",
                            fontSize = 17.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = inlineMarkdown(block.text),
                            fontSize = 17.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    is MarkdownBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = inlineMarkdown(block.text),
                            fontSize = 16.sp,
                            lineHeight = 27.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is MarkdownBlock.Divider -> HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private sealed interface Segment {
    data class Text(val value: String) : Segment
    data class Code(val language: String, val value: String) : Segment
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Ordered(val number: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private fun parseCodeSegments(content: String): List<Segment> {
    val result = mutableListOf<Segment>()
    val regex = Regex("```([\\w+#.-]*)\\s*\\n?([\\s\\S]*?)```")
    var cursor = 0
    regex.findAll(content).forEach { match ->
        if (match.range.first > cursor) result += Segment.Text(content.substring(cursor, match.range.first))
        result += Segment.Code(match.groupValues[1], match.groupValues[2])
        cursor = match.range.last + 1
    }
    if (cursor < content.length) result += Segment.Text(content.substring(cursor))
    if (result.isEmpty()) result += Segment.Text(content)
    return result
}

private fun parseMarkdownBlocks(value: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    value.trim().lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val heading = Regex("^(#{1,6})\\s+(.+?)(?:\\s+#+)?$").find(line)
        val bullet = Regex("^[-*+]\\s+(.+)$").find(line)
        val ordered = Regex("^(\\d{1,3})[.、]\\s+(.+)$").find(line)
        when {
            line.isBlank() -> flushParagraph()
            heading != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length.coerceAtMost(3), heading.groupValues[2])
            }
            bullet != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(bullet.groupValues[1])
            }
            ordered != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Ordered(ordered.groupValues[1].toInt(), ordered.groupValues[2])
            }
            line.startsWith(">") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(line.removePrefix(">").trim())
            }
            Regex("^([-*_])\\1{2,}$").matches(line) -> {
                flushParagraph()
                blocks += MarkdownBlock.Divider
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    flushParagraph()
    return blocks
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val regex = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*\\s][^*]*\\*|`[^`]+`|\\[[^\\]]+]\\([^)]+\\))")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val value = match.value
        when {
            value.startsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(value.removeSurrounding("**"))
                pop()
            }
            value.startsWith("*") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(value.removeSurrounding("*"))
                pop()
            }
            value.startsWith("`") -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x18000088)))
                append(value.removeSurrounding("`"))
                pop()
            }
            else -> {
                val label = Regex("^\\[([^\\]]+)]").find(value)?.groupValues?.get(1).orEmpty().ifBlank { value }
                pushStyle(SpanStyle(color = Color(0xFF4D6BFE), textDecoration = TextDecoration.None))
                append(label)
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
