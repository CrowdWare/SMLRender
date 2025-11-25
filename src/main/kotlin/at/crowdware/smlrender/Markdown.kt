package at.crowdware.smlrender

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Minimal inline markdown parser supporting **bold**, *italic*, links [text](url),
 * and heading prefixes (#, ##, ###).
 */
fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val styleStack = ArrayDeque<SpanStyle>()
    fun toggle(style: SpanStyle) {
        if (styleStack.isNotEmpty() && styleStack.last() == style) {
            pop()
            styleStack.removeLast()
        } else {
            pushStyle(style)
            styleStack.addLast(style)
        }
    }

    while (i < text.length) {
        if (text.startsWith("**", i)) {
            toggle(SpanStyle(fontWeight = FontWeight.Bold))
            i += 2
            continue
        }
        if (text.startsWith("[", i)) {
            val closeText = text.indexOf(']', startIndex = i + 1)
            val openParen = if (closeText != -1) text.indexOf('(', startIndex = closeText) else -1
            val closeParen = if (openParen != -1) text.indexOf(')', startIndex = openParen) else -1
            if (closeText != -1 && openParen == closeText + 1 && closeParen != -1) {
                val label = text.substring(i + 1, closeText)
                val url = text.substring(openParen + 1, closeParen)
                val start = length
                append(label)
                addStringAnnotation("URL", url, start, start + label.length)
                i = closeParen + 1
                continue
            }
        }
        val c = text[i]
        if (c == '*') {
            toggle(SpanStyle(fontStyle = FontStyle.Italic))
            i += 1
            continue
        }
        append(c)
        i += 1
    }
}

/** Detect heading level from leading # and return Pair(level, content without heading markers). */
fun stripHeading(raw: String): Pair<Int, String> {
    val trimmed = raw.trimStart()
    var level = 0
    var idx = 0
    while (idx < trimmed.length && trimmed[idx] == '#' && level < 6) {
        level++
        idx++
    }
    val content = if (level > 0 && idx < trimmed.length && trimmed[idx].isWhitespace()) {
        trimmed.substring(idx + 1)
    } else if (level > 0) {
        trimmed.substring(level)
    } else trimmed
    return level to content.trimStart()
}
