package com.innernet.smlrender

/**
 * Placeholder SML Render library to be shared between InnerNet und WP ComposeApp.
 * Ersetzt die einfachen Parser/Renderer-Stubs hier mit dem bestehenden Kotlin SML Parser
 * und Compose-basiertem Renderer.
 */
object SmlRender {
    fun renderToHtml(sml: String, options: RenderOptions = RenderOptions()): RenderResult {
        val ast = SmlParser.parse(sml)
        val html = SmlHtmlRenderer.render(ast, options)
        return RenderResult(html = html, errors = ast.errors)
    }
}

data class RenderOptions(
    val prettyPrint: Boolean = false,
)

data class RenderResult(
    val html: String,
    val errors: List<SmlError> = emptyList(),
)

data class SmlError(val message: String, val line: Int? = null, val column: Int? = null)

data class SmlAst(val raw: String, val errors: List<SmlError> = emptyList())

object SmlParser {
    fun parse(input: String): SmlAst {
        // TODO: Hook in existing Kotlin SML parser here
        return SmlAst(raw = input)
    }
}

object SmlHtmlRenderer {
    fun render(ast: SmlAst, options: RenderOptions): String {
        // TODO: Replace with proper renderer. For now, HTML-escape the raw SML as a placeholder.
        val escaped = ast.raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return if (options.prettyPrint) "<pre>" + escaped + "</pre>" else escaped
    }
}
