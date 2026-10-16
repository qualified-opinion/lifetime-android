package app.lifetime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MetadataParsingTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun exifDateWithOffsetIsExact() {
        val millis = MetadataParsing.parseExifDate("2026:10:16 14:32:15", "+02:00", berlin)
        assertEquals(Instant.parse("2026-10-16T12:32:15Z").toEpochMilli(), millis)
    }

    @Test
    fun exifDateWithoutOffsetUsesGivenZone() {
        val millis = MetadataParsing.parseExifDate("2026:01:05 08:00:00", null, berlin)
        assertEquals(Instant.parse("2026-01-05T07:00:00Z").toEpochMilli(), millis) // CET = UTC+1
    }

    @Test
    fun exifDateRejectsPlaceholdersAndJunk() {
        assertNull(MetadataParsing.parseExifDate("0000:00:00 00:00:00", null, berlin))
        assertNull(MetadataParsing.parseExifDate("1970:01:01 00:00:00", "+00:00", berlin))
        assertNull(MetadataParsing.parseExifDate("not a date", null, berlin))
        assertNull(MetadataParsing.parseExifDate("  ", null, berlin))
        assertNull(MetadataParsing.parseExifDate(null, null, berlin))
    }

    @Test
    fun videoDateIsUtc() {
        assertEquals(
            Instant.parse("2026-10-16T14:32:15Z").toEpochMilli(),
            MetadataParsing.parseVideoDate("20261016T143215.000Z"),
        )
        assertEquals(
            Instant.parse("2026-10-16T14:32:15Z").toEpochMilli(),
            MetadataParsing.parseVideoDate("20261016T143215Z"),
        )
    }

    @Test
    fun videoZeroDatesAreIgnored() {
        assertNull(MetadataParsing.parseVideoDate("19040101T000000.000Z"))
        assertNull(MetadataParsing.parseVideoDate("19700101T000000.000Z"))
        assertNull(MetadataParsing.parseVideoDate(""))
    }

    @Test
    fun iso6709Location() {
        assertEquals(37.4219 to -122.084, MetadataParsing.parseIso6709("+37.4219-122.0840/"))
        assertEquals(-33.8688 to 151.2093, MetadataParsing.parseIso6709("-33.8688+151.2093+012.5/"))
        assertNull(MetadataParsing.parseIso6709("garbage"))
        assertNull(MetadataParsing.parseIso6709("+95.0+10.0/"))
        assertNull(MetadataParsing.parseIso6709(null))
    }

    @Test
    fun providerDatesInSecondsOrMillis() {
        val millis = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
        assertEquals(millis, MetadataParsing.normaliseProviderDate(millis / 1000))
        assertEquals(millis, MetadataParsing.normaliseProviderDate(millis))
        assertNull(MetadataParsing.normaliseProviderDate(0))
        assertNull(MetadataParsing.normaliseProviderDate(-1))
    }
}
