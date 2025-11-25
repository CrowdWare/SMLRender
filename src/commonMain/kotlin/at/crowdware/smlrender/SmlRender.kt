@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package at.crowdware.smlrender

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val LocalButtonLinkHandler = compositionLocalOf<(String) -> Unit> { { _ -> } }
private val LocalButtonScriptHandler = compositionLocalOf<(String) -> Unit> { { _ -> } }
private val LocalPageTitleHandler = compositionLocalOf<(String) -> Unit> { { _ -> } }
private val LocalPageBackgroundHandler = compositionLocalOf<(String?) -> Unit> { { _ -> } }
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

private fun Map<String, PropertyValue>.bool(key: String, default: Boolean = false): Boolean =
    (this[key] as? PropertyValue.BooleanValue)?.value ?: default

private fun Map<String, PropertyValue>.numberString(key: String): Float? =
    string(key)?.toFloatOrNull()

private fun Map<String, PropertyValue>.opacity(key: String = "opacity"): Float? {
    float(key)?.let { return it.coerceIn(0f, 1f) }
    int(key)?.let { return (it / 100f).coerceIn(0f, 1f) }
    numberString(key)?.let { return it.coerceIn(0f, 1f) }
    return null
}

@Composable
private fun Map<String, PropertyValue>.color(key: String): Color? =
    parseColor(string(key))

private fun Map<String, PropertyValue>.fontSize(): androidx.compose.ui.unit.TextUnit? {
    float("fontSize")?.let { return it.sp }
    int("fontSize")?.let { return it.sp }
    numberString("fontSize")?.let { return it.sp }
    return null
}

private fun Map<String, PropertyValue>.fontWeight(): FontWeight? =
    when (string("fontWeight")?.lowercase()) {
        "thin", "100" -> FontWeight.Thin
        "extralight", "200" -> FontWeight.ExtraLight
        "light", "300" -> FontWeight.Light
        "normal", "regular", "400" -> FontWeight.Normal
        "medium", "500" -> FontWeight.Medium
        "semibold", "600" -> FontWeight.SemiBold
        "bold", "700" -> FontWeight.Bold
        "extrabold", "800" -> FontWeight.ExtraBold
        "black", "900" -> FontWeight.Black
        else -> null
    }

@Composable
private fun parseColor(raw: String?): Color? {
    val value = raw?.trim() ?: return null
    val named = when (value.lowercase()) {
        "primary" -> MaterialTheme.colorScheme.primary
        "onprimary" -> MaterialTheme.colorScheme.onPrimary
        "secondary" -> MaterialTheme.colorScheme.secondary
        "background" -> MaterialTheme.colorScheme.background
        "surface" -> MaterialTheme.colorScheme.surface
        "onbackground" -> MaterialTheme.colorScheme.onBackground
        "onsurface" -> MaterialTheme.colorScheme.onSurface
        else -> null
    }
    if (named != null) return named
    return parseHexColor(value)
}

private fun parseHexColor(value: String): Color? {
    val hex = value.removePrefix("#")
    val parsed = hex.toLongOrNull(16) ?: return null
    val argb = when (hex.length) {
        6 -> (0xFF000000L or parsed)
        8 -> parsed
        else -> return null
    }
    val a = ((argb shr 24) and 0xFF).toInt()
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()
    return Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
        alpha = a / 255f
    )
}

private fun paddingValues(props: Map<String, PropertyValue>, defaultAll: Int): androidx.compose.foundation.layout.PaddingValues {
    props.int("padding")?.let { return androidx.compose.foundation.layout.PaddingValues(it.dp) }
    val raw = props.string("padding")?.trim() ?: return androidx.compose.foundation.layout.PaddingValues(defaultAll.dp)
    val parts = raw.split(Regex("[,\\s]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
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
    val modifier = baseModifier.padding(pad)
    Column(
        modifier = modifier,
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
private fun renderCard(
    node: SmlNode,
    baseModifier: Modifier = Modifier,
    renderChild: @Composable ColumnScope.(SmlNode) -> Unit
) {
    val maxWidth = node.properties.int("maxWidth")?.takeIf { it > 0 }?.dp
    val backgroundColor = node.properties.color("backgroundColor") ?: MaterialTheme.colorScheme.surface
    val radius = (node.properties.int("borderRadius") ?: 0).dp
    val elevation = (node.properties.int("elevation") ?: 0).dp
    val widthModifier = maxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier.fillMaxWidth()
    val modifier = baseModifier.then(widthModifier)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        renderColumnNode(node = node, renderChild = renderChild)
    }
}

@Composable
private fun renderLink(node: SmlNode) {
    val label = node.properties.string("text").orEmpty()
    val href = node.properties.string("href") ?: node.properties.string("link") ?: ""
    ClickableText(
        text = AnnotatedString(label),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    ) { console.log("open url: $href") }
}

@Composable
private fun renderAccordion(
    node: SmlNode,
    renderChild: @Composable ColumnScope.(SmlNode) -> Unit
) {
    val title = node.properties.string("title").orEmpty()
    val initialOpen = when (node.properties.string("initiallyOpen")?.lowercase()) {
        "true", "1", "yes", "y" -> true
        else -> node.properties.int("initiallyOpen") == 1
    }
    var open by remember(node) { mutableStateOf(initialOpen) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleMedium)
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                node.children.forEach { child -> renderChild(child) }
            }
        }
    }
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
    }.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = node.properties.fontSize() ?: MaterialTheme.typography.bodyMedium.fontSize,
        fontWeight = node.properties.fontWeight() ?: MaterialTheme.typography.bodyMedium.fontWeight
    )
    val opacity = node.properties.opacity()
    val modifier = if (opacity != null) Modifier.alpha(opacity) else Modifier
    ClickableText(text = annotated, style = style, modifier = modifier) { offset ->
        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()
            ?.let { console.log("open url: ${it.item}") }
    }
}

@Composable
private fun renderButton(node: SmlNode, renderChild: @Composable (SmlNode) -> Unit) {
    val rawLabel = node.properties.string("label") ?: node.properties.string("text")
    val engine = LocalScriptEngine.current
    val strings = LocalStrings.current
    val txt = resolveBoundLabel(rawLabel, engine, strings)
    val linkHandler = LocalButtonLinkHandler.current
    val scriptHandler = LocalButtonScriptHandler.current
    val link = node.properties.string("link")
    val script = node.properties.string("onClick")
    val backgroundColor = node.properties.color("color")
    val colors = backgroundColor?.let {
        ButtonDefaults.buttonColors(
            containerColor = it,
            contentColor = MaterialTheme.colorScheme.contentColorFor(it)
        )
    } ?: ButtonDefaults.buttonColors()
    Button(onClick = {
        if (script != null) scriptHandler(script)
        if (link != null) linkHandler(link)
    }, colors = colors) {
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
    val opacity = node.properties.opacity()
    val modifier = if (opacity != null) Modifier.alpha(opacity) else Modifier
    val fontSize = node.properties.fontSize()
    val weight = node.properties.fontWeight()
    val base = MaterialTheme.typography.bodyMedium
    val style = base.copy(
        fontSize = fontSize ?: base.fontSize,
        fontWeight = weight ?: base.fontWeight
    )
    Text(resolved, modifier = modifier, style = style)
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
    onPageBackground: (String?) -> Unit = { _ -> },
    scriptEngine: ScriptEngine? = null,
    strings: Map<String, String> = emptyMap()
) {
    CompositionLocalProvider(
        LocalButtonLinkHandler provides onLinkClick,
        LocalButtonScriptHandler provides onScriptClick,
        LocalPageTitleHandler provides onPageTitle,
        LocalPageBackgroundHandler provides onPageBackground,
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
            val background = node.properties.string("background")
            val backgroundHandler = LocalPageBackgroundHandler.current
            SideEffect { backgroundHandler(background) }
            val scrollable = node.properties.bool("scrollable", default = false)
            val scrollState = if (scrollable) rememberScrollState() else null
            val pageModifier = Modifier
                .fillMaxSize()
                .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier)
            renderColumnNode(
                node = node,
                baseModifier = pageModifier,
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
        "Card" -> renderCard(node = node) { child -> renderNodeInColumn(child) }
        "Accordion" -> renderAccordion(node = node) { child -> renderNodeInColumn(child) }
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
        "Card" -> {
            renderCard(node = node) { child -> renderNodeInColumn(child) }
        }
        "Accordion" -> {
            renderAccordion(node = node) { child -> renderNodeInColumn(child) }
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
        "Card" -> {
            renderCard(node = node) { child -> renderNodeInColumn(child) }
        }
        "Accordion" -> {
            renderAccordion(node = node) { child -> renderNodeInColumn(child) }
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
