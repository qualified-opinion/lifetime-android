package app.lifetime.data

/** Rules for the text a moment can carry. Plain Kotlin so they are covered by JVM tests. */
object Captions {
    /** Long enough for a proper post, short enough to stay a caption. */
    const val MAX_LENGTH = 2000

    /** Trims the ends (inner line breaks stay), turns blank into null and caps the length. */
    fun normalize(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.length <= MAX_LENGTH) return trimmed
        // Don't split a surrogate pair (an emoji) at the cut.
        val end = if (trimmed[MAX_LENGTH - 1].isHighSurrogate()) MAX_LENGTH - 1 else MAX_LENGTH
        return trimmed.substring(0, end).trimEnd()
    }
}
