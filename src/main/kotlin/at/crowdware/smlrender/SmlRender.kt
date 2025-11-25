@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package at.crowdware.smlrender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import at.crowdware.sms.ScriptEngine
import at.crowdware.sml.PropertyValue
import at.crowdware.sml.SmlHandler
import at.crowdware.sml.SmlParseException
import at.crowdware.sml.SmlSaxParser
import at.crowdware.sml.Span
import kotlin.js.console

data class SmlNode(
    val name: String,
    val properties: Map<String, PropertyValue>,
    val children: List<SmlNode>
)

private val LocalButtonLinkHandler = compositionLocalOf<(String) -> Unit> { {} }
private val LocalButtonScriptHandler = compositionLocalOf<(String) -> Unit> { {} }
private val LocalPageTitleHandler = compositionLocalOf<(String) -> Unit> { {} }
private val LocalScriptEngine = compositionLocalOf<ScriptEngine?> { null }
private val LocalStrings = compositionLocalOf<Map<String, String>> { emptyMap() }

// ---------- Parsing ----------

private class NodeBuildingHandler : SmlHandler {
    private val stack = ArrayDeque<MutableNode>()
    private val roots = mutableListOf<MutableNode>()

    override fun startElement(name: String) {
        stack.addLast(MutableNode(name))
    }

    override fun onProperty(name: String, value: PropertyValue) {
        val cur = stack.lastOrNull() ?: throw SmlParseException("Property outside of element", Span(0, 0, 0))
        cur.props[name] = value
    }

    override fun endElement(name: String) {
        val node = stack.removeLastOrNull() ?: throw SmlParseException("Mismatched end '$name'", Span(0, 0, 0))
        if (node.name != name) throw SmlParseException("Expected end of ${node.name}, got $name", Span(0, 0, 0))
        if (stack.isEmpty()) roots += node else stack.last().children += node
    }

    fun result(): List<SmlNode> = roots.map { it.toImmutable() }

    private data class MutableNode(
        val name: String,
        val props: MutableMap<String, PropertyValue> = LinkedHashMap(),
        val children: MutableList<MutableNode> = mutableListOf()
    ) {
        fun toImmutable(): SmlNode = SmlNode(name, props.toMap(), children.map { it.toImmutable() })
    }
}

internal fun parseSml(text: String): List<SmlNode> {
    val handler = NodeBuildingHandler()
    SmlSaxParser(text).parse(handler)
    return handler.result()
}

// ---------- Helpers ----------

private fun Map<String, PropertyValue>.string(key: String): String? =
    (this[key] as? PropertyValue.StringValue)?.value

private fun Map<String, PropertyValue>.int(key: String): Int? =
    (this[key] as? PropertyValue.IntValue)?.value

private fun Map<String, PropertyValue>.float(key: String): Float? =
    (this[key] as? PropertyValue.FloatValue)?.value

private fun paddingValues(props: Map<String, PropertyValue>, defaultAll: Int): androidx.compose.foundation.layout.PaddingValues {
    props.int("padding")?.let { return androidx.compose.foundation.layout.PaddingValues(it.dp) }
    val raw = props.string("padding")?.trim() ?: return androidx.compose.foundation.layout.PaddingValues(defaultAll.dp)
    val parts = raw.split(Regex("\\s+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        1 -> androidx.compose.foundation.layout.PaddingValues(parts[0].dp)
        2 -> androidx.compose.foundation.layout.PaddingValues(horizontal = parts[1].dp, vertical = parts[0].dp)
        4 -> androidx.compose.foundation.layout.PaddingValues(
            start = parts[0].dp,
            top = parts[1].dp,
            end = parts[2].dp,
            bottom = parts[3].dp
        )
        else -> androidx.compose.foundation.layout.PaddingValues(defaultAll.dp)
    }
}

@Composable
private fun renderColumnNode(
    node: SmlNode,
    baseModifier: Modifier = Modifier.fillMaxWidth(),
    defaultPadding: Int = 0,
    defaultSpacing: Int = 0,
    renderChild: @Composable ColumnScope.(SmlNode) -> Unit
) {
    val spacing = node.properties.int("spacing") ?: defaultSpacing
    val pad = paddingValues(node.properties, defaultAll = defaultPadding)
    val align = when (node.properties.string("alignment")) {
        "center" -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
    Column(
        modifier = baseModifier.padding(pad),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        horizontalAlignment = align
    ) {
        node.children.forEach { child -> renderChild(child) }
    }
}

@Composable
private fun renderRowNode(
    node: SmlNode,
    baseModifier: Modifier = Modifier.fillMaxWidth(),
    defaultPadding: Int = 0,
    defaultSpacing: Int = 0,
    renderChild: @Composable RowScope.(SmlNode) -> Unit
) {
    val spacing = node.properties.int("spacing") ?: defaultSpacing
    val pad = paddingValues(node.properties, defaultAll = defaultPadding)
    Row(
        modifier = baseModifier.padding(pad),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        node.children.forEach { child -> renderChild(child) }
    }
}

@Composable
private fun renderLink(node: SmlNode) {
    val label = node.properties.string("text").orEmpty()
    val href = node.properties.string("href").orEmpty()
    ClickableText(
        text = AnnotatedString(label),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    ) { console.log("open url: $href") }
}

@Composable
private fun renderMarkdown(node: SmlNode) {
    val engine = LocalScriptEngine.current
    val strings = LocalStrings.current
    val raw = node.properties.string("text") ?: ""
    val resolvedRaw = resolveBoundLabel(raw, engine, strings) ?: raw
    val (heading, body) = stripHeading(resolvedRaw)
    val annotated: AnnotatedString = parseInlineMarkdown(body)
    val style = when (heading) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.bodyMedium
    }.copy(color = MaterialTheme.colorScheme.onBackground)
    ClickableText(text = annotated, style = style) { offset ->
        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()
            ?.let { console.log("open url: ${it.item}") }
    }
}

@Composable
private fun renderButton(node: SmlNode, renderChild: @Composable (SmlNode) -> Unit) {
    val rawLabel = node.properties.string("label")
    val engine = LocalScriptEngine.current
    val strings = LocalStrings.current
    val txt = resolveBoundLabel(rawLabel, engine, strings)
    val linkHandler = LocalButtonLinkHandler.current
    val scriptHandler = LocalButtonScriptHandler.current
    val link = node.properties.string("link")
    val script = node.properties.string("onClick")
    Button(onClick = {
        if (script != null) scriptHandler(script)
        if (link != null) linkHandler(link)
    }) {
        if (txt != null) Text(txt)
        node.children.forEach { child -> renderChild(child) }
    }
}

@Composable
private fun renderText(node: SmlNode) {
    val engine = LocalScriptEngine.current
    val strings = LocalStrings.current
    val raw = node.properties.string("text") ?: ""
    val resolved = resolveBoundLabel(raw, engine, strings) ?: raw
    Text(resolved)
}

private fun resolveBoundLabel(raw: String?, engine: ScriptEngine?, strings: Map<String, String>): String? {
    if (raw == null || engine == null) return raw
    fun evalVar(name: String): String? =
        runCatching { engine.executeAndGetKotlin(name) }.onFailure {
            setStatusMessage(engine, it.message ?: it.toString())
        }.getOrNull()?.toString()
    return when {
        raw.startsWith("string:") -> strings[raw.removePrefix("string:").trim()] ?: raw
        raw.startsWith("model:") -> evalVar(raw.removePrefix("model:").trim()).orEmpty()
        raw.startsWith("global:") -> evalVar(raw.removePrefix("global:").trim()).orEmpty()
        else -> raw
    }
}

private fun setStatusMessage(engine: ScriptEngine?, message: String) {
    if (engine == null) return
    val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
    runCatching { engine.execute("""statusMessage = \"$escaped\"""") }
}

// ---------- Rendering entry ----------

@Composable
fun RenderSml(
    text: String,
    onLinkClick: (String) -> Unit = {},
    onScriptClick: (String) -> Unit = {},
    onPageTitle: (String) -> Unit = {},
    scriptEngine: ScriptEngine? = null,
    strings: Map<String, String> = emptyMap()
) {
    CompositionLocalProvider(
        LocalButtonLinkHandler provides onLinkClick,
        LocalButtonScriptHandler provides onScriptClick,
        LocalPageTitleHandler provides onPageTitle,
        LocalScriptEngine provides scriptEngine,
        LocalStrings provides strings
    ) {
        val roots = remember(text) { parseSml(text) }
        val page = roots.firstOrNull { it.name == "Page" }
        if (page != null) {
            renderNode(page)
        } else {
            roots.forEach { renderNode(it) }
        }
    }
}

// ---------- Neutral render (no parent scope) ----------

@Composable
private fun renderNode(node: SmlNode) {
    when (node.name) {
        "Page" -> {
            node.properties.string("title")?.let { title ->
                val titleHandler = LocalPageTitleHandler.current
                SideEffect { titleHandler(title) }
            }
            renderColumnNode(
                node = node,
                baseModifier = Modifier.fillMaxSize(),
                defaultPadding = 16,
                defaultSpacing = 16,
                renderChild = { child -> renderNodeInColumn(child) }
            )
        }
        "Column" -> {
            renderColumnNode(node = node) { child -> renderNodeInColumn(child) }
        }
        "Row" -> {
            renderRowNode(node = node) { child -> renderNodeInRow(child) }
        }
        "Spacer" -> Spacer(Modifier.height((node.properties.int("amount") ?: 0).dp))
        "Text" -> renderText(node)
        "Link" -> renderLink(node)
        "Markdown" -> renderMarkdown(node)
        "Button" -> renderButton(node) { child -> renderNode(child) }
        else -> {
            node.children.forEach { child -> renderNode(child) }
        }
    }
}

// ---------- Column scope render ----------

@Composable
private fun ColumnScope.renderNodeInColumn(node: SmlNode) {
    when (node.name) {
        "Column" -> {
            renderColumnNode(node = node) { child -> renderNodeInColumn(child) }
        }
        "Row" -> {
            renderRowNode(node = node) { child -> renderNodeInRow(child) }
        }
        "Spacer" -> {
            val amount = node.properties.int("amount") ?: 0
            val w = node.properties.int("weight")?.toFloat()
            val mod = if (w != null) Modifier.weight(weight = w) else Modifier.height(amount.dp)
            Spacer(mod)
        }
        "Text" -> renderText(node)
        "Link" -> renderLink(node)
        "Markdown" -> renderMarkdown(node)
        "Button" -> renderButton(node) { child -> renderNode(child) }
        else -> {
            node.children.forEach { child -> renderNodeInColumn(child) }
        }
    }
}

// ---------- Row scope render ----------

@Composable
private fun RowScope.renderNodeInRow(node: SmlNode) {
    when (node.name) {
        "Row" -> {
            renderRowNode(node = node) { child -> renderNodeInRow(child) }
        }
        "Column" -> {
            renderColumnNode(node = node) { child -> renderNodeInColumn(child) }
        }
        "Spacer" -> {
            val amount = node.properties.int("amount") ?: 0
            val w = node.properties.int("weight")?.toFloat()
            val mod = if (w != null) Modifier.weight(weight = w) else Modifier.width(amount.dp)
            Spacer(mod)
        }
        "Text" -> renderText(node)
        "Link" -> renderLink(node)
        "Markdown" -> renderMarkdown(node)
        "Button" -> renderButton(node) { child -> renderNode(child) }
        else -> {
            node.children.forEach { child -> renderNodeInRow(child) }
        }
    }
}

// ---------- Box scope render ----------

@Composable
fun BoxScope.RenderNode(node: SmlNode) {
    renderNode(node)
}
