package org.phalanx.android.core.format

/**
 * Removes [TrackingParameters] from the links in a message before it leaves the phone. Only whole
 * query parameters are dropped: the path, the fragment and every other parameter keep their exact
 * bytes, and links inside `code` stay as written, so a link opens the same page it did before.
 */
internal object LinkTrackers {
    fun strip(text: String): String {
        if ('?' !in text || "://" !in text) return text
        return CANDIDATES.replace(text) { match -> match.groups[1]?.value?.let(::clean) ?: match.value }
    }

    private fun clean(url: String): String {
        val query = queryOf(url) ?: return url
        val site = hostOf(url)?.let(TrackingParameters::forHost)
        val params = url.substring(query.first, query.last + 1).split('&').filter { it.isNotEmpty() }
        val kept = params.filter { !isTracking(it, site) }
        return if (kept.size == params.size) {
            url
        } else {
            buildString(url.length) {
                append(url, 0, query.first - 1)
                if (kept.isNotEmpty()) kept.joinTo(this, "&", prefix = "?")
                append(url, query.last + 1, url.length)
            }
        }
    }

    /** Where the parameters of [url] are, after its "?", or null when it has none of its own. */
    private fun queryOf(url: String): IntRange? {
        val end = linkEnd(url)
        val query = url.indexOf('?')
        val hash = url.indexOf('#')
        // A "?" after a "#" belongs to the fragment, as in single-page apps' "/#/route?x=1".
        if (query !in 0 until end || hash in 0 until query) return null
        val fragment = if (hash in query until end) hash else end
        return query + 1 until fragment
    }

    /** `__cft__[0]=` is named `__cft__`; `utm_Source` is `utm_source`. */
    private fun isTracking(param: String, site: Set<String>?): Boolean {
        val name = param.substringBefore('=').substringBefore('[').lowercase()
        return name.isNotEmpty() && TrackingParameters.isTracking(name, site)
    }

    private fun hostOf(url: String): String? {
        val start = url.indexOf("://") + SCHEME_SEPARATOR
        val end = url.indexOfAny(HOST_END, start).let { if (it < 0) url.length else it }
        return url.substring(start, end).substringAfterLast('@').substringBefore(':').trimEnd('.').lowercase()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Where the link really ends: punctuation closing the sentence around it is not part of it, nor
     * is a ")" closing a markdown link or a remark. A ")" it opened itself stays, as Wikipedia uses.
     */
    private fun linkEnd(url: String): Int {
        var end = url.length
        while (end > 0) {
            val last = url[end - 1]
            val closesOutside = last == ')' && url.count(end, '(') < url.count(end, ')')
            if (last !in TRAILING && !closesOutside) break
            end--
        }
        return end
    }

    private fun String.count(end: Int, char: Char): Int = (0 until end).count { this[it] == char }

    private const val SCHEME_SEPARATOR = 3
    private const val TRAILING = ".,;:!?'\"*_~|>"
    private val HOST_END = charArrayOf('/', '?', '#')

    // Code first, so a link in a code span or block is consumed whole and never reaches group 1.
    // Brackets only in pairs, as in `__cft__[0]=`: a lone "]" ends a markdown label, not a link.
    private val CANDIDATES = Regex(
        """```[\s\S]*?```|`[^`\n]*`|(https?://(?:[^\s<>"`\[\]]|\[[^\s<>"`\[\]]*])+)""",
        RegexOption.IGNORE_CASE
    )
}
