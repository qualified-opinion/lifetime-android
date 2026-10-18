package app.lifetime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionsTest {
    @Test fun blankBecomesNull() {
        assertNull(Captions.normalize(null))
        assertNull(Captions.normalize(""))
        assertNull(Captions.normalize(" \n\t "))
    }

    @Test fun trimsTheEndsButKeepsInnerLineBreaks() {
        assertEquals("Day one.\n\nStill raining.", Captions.normalize("\n  Day one.\n\nStill raining.  \n"))
    }

    @Test fun capsTheLength() {
        assertEquals(Captions.MAX_LENGTH, Captions.normalize("a".repeat(5000))!!.length)
        assertEquals("a".repeat(Captions.MAX_LENGTH), Captions.normalize("a".repeat(Captions.MAX_LENGTH)))
    }

    @Test fun doesNotCutAnEmojiInHalf() {
        val text = "a".repeat(Captions.MAX_LENGTH - 1) + "😀" // ends with 😀 across the limit
        val result = Captions.normalize(text)!!
        assertEquals("a".repeat(Captions.MAX_LENGTH - 1), result)
    }
}
