package omnibeat.app.data

import android.content.res.Resources
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** TXT files contain Android string-resource XML, not a second translation format. */
object TranslationCodec {
    private const val MAX_BYTES = 1_048_576
    private data class Entry(val value: String, val translatable: Boolean)

    fun export(resources: Resources): String {
        val directories = resources.assets.list("translations").orEmpty().toSet()
        val locales = resources.configuration.locales
        val directory = (0 until locales.size()).firstNotNullOfOrNull { index ->
            val locale = locales[index]
            if (locale.language == "en") return@firstNotNullOfOrNull "values"
            listOf(
                "values-b+${locale.toLanguageTag().replace('-', '+')}",
                "values-${locale.language}-r${locale.country}",
                "values-${locale.language}",
            ).firstOrNull { it in directories }
        } ?: "values"
        return resources.assets.open("translations/$directory/strings.xml")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun read(input: InputStream): String {
        val bytes = input.readNBytes(MAX_BYTES + 1)
        require(bytes.size <= MAX_BYTES) { "Translation file is larger than 1 MB" }
        return bytes.toString(Charsets.UTF_8)
    }

    fun decode(text: String, resources: Resources): Map<String, String> {
        val baseline = resources.assets.open("translations/values/strings.xml")
            .bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        val imported = parse(text)
        val result = linkedMapOf<String, String>()
        imported.forEach { (name, entry) ->
            val original = requireNotNull(baseline[name]) { "Unknown string: $name" }
            if (!original.translatable) {
                require(entry.value == original.value) { "String cannot be translated: $name" }
            } else {
                validatePlaceholders(name, original.value, entry.value)
                result[name] = entry.value
            }
        }
        require(result.isNotEmpty()) { "No translatable strings found" }
        return result
    }

    private fun parse(text: String): Map<String, Entry> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Translation file is larger than 1 MB" }
        val parser = Xml.newPullParser()
        parser.setInput(text.removePrefix("\uFEFF").reader())
        val entries = linkedMapOf<String, Entry>()
        var rootSeen = false
        var rootClosed = false
        var name: String? = null
        var translatable = true
        val value = StringBuilder()
        while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("DOCTYPE is not supported")
                XmlPullParser.START_TAG -> when (parser.depth) {
                    1 -> {
                        require(!rootSeen && parser.name == "resources") { "Expected one <resources> element" }
                        rootSeen = true
                    }
                    2 -> {
                        require(parser.name == "string") { "Only <string> entries are supported" }
                        name = requireNotNull(parser.getAttributeValue(null, "name")) { "Missing string name" }
                        require(name !in entries) { "Duplicate string: $name" }
                        translatable = parser.getAttributeValue(null, "translatable") != "false"
                        value.clear()
                    }
                    else -> error("Nested markup is not supported in string: $name")
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF,
                XmlPullParser.IGNORABLE_WHITESPACE -> {
                    val content = requireNotNull(parser.text) { "Unknown XML entity" }
                    if (name != null) value.append(content)
                    else require(content.isBlank()) { "Text outside a <string> element" }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == 2) {
                        entries[requireNotNull(name)] = Entry(decodeAndroidText(value.toString()), translatable)
                        name = null
                    } else if (parser.depth == 1) rootClosed = true
                }
            }
        }
        require(rootSeen && rootClosed) { "Incomplete <resources> document" }
        return entries
    }

    // Android resource quoting/escapes are applied after XML entity decoding.
    private fun decodeAndroidText(text: String): String {
        val result = StringBuilder()
        var quoted = false
        var pendingSpace = false
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (char == '"') {
                quoted = !quoted
                continue
            }
            if (char.isWhitespace() && !quoted) {
                pendingSpace = result.isNotEmpty()
                continue
            }
            if (pendingSpace) result.append(' ')
            pendingSpace = false
            if (char == '\\') {
                require(index < text.length) { "Incomplete escape sequence" }
                when (val escaped = text[index++]) {
                    'n' -> result.append('\n')
                    't' -> result.append('\t')
                    'r' -> result.append('\r')
                    'u' -> {
                        require(index + 4 <= text.length) { "Incomplete Unicode escape" }
                        result.append(text.substring(index, index + 4).toInt(16).toChar())
                        index += 4
                    }
                    else -> result.append(escaped)
                }
            } else result.append(char)
        }
        require(!quoted) { "Unclosed quote in string" }
        return result.toString()
    }

    private val placeholder = Regex("%(?:[1-9][0-9]*\\$)?[ds]|%%")

    private fun validatePlaceholders(name: String, original: String, translated: String) {
        val expected = placeholder.findAll(original).map { it.value }.filter { it != "%%" }.sorted().toList()
        if (expected.isEmpty()) return
        val actual = placeholder.findAll(translated).map { it.value }.filter { it != "%%" }.sorted().toList()
        require(actual == expected && !placeholder.replace(translated, "").contains('%')) {
            "Keep the format placeholders unchanged in $name: ${expected.joinToString()}"
        }
    }
}
