package app.lifetime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MetadataParsingTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-16T18:00:00Z").toEpochMilli()

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

    @Test fun knownFilenameTimestampsUseLocalTime() {
        val expected = Instant.parse("2026-10-16T12:32:15Z").toEpochMilli()
        listOf(
            "IMG_20261016_143215.jpg", "PXL_20261016_143215.jpg",
            "PXL_20261016_143215123.jpg", "VID_20261016_143215.mp4",
            "IMG_20261016_143215_120.jpg", "2026-10-16 14.32.15.jpg",
        ).forEach { assertEquals(it, expected, MetadataParsing.parseDisplayName(it, berlin, now)) }
    }

    @Test fun pixelFilenameTimestampsAllowRepeatedExtensions() {
        val expected = Instant.parse("2026-10-16T12:32:15Z").toEpochMilli()
        listOf("MP", "PORTRAIT", "NIGHT").forEach { suffix ->
            val name = "PXL_20261016_143215123.$suffix.jpg"
            assertEquals(name, expected, MetadataParsing.parseDisplayName(name, berlin, now))
        }
    }

    @Test fun dateOnlyFilenamesUseNoonInTheLocalZone() {
        val expected = Instant.parse("2026-10-16T10:00:00Z").toEpochMilli()
        listOf("IMG-20261016-WA0001.jpg", "VID-20261016-WA0001.mp4", "IMG_20261016.jpg", "PXL_20261016.jpg", "VID_20261016.mp4").forEach {
            assertEquals(it, expected, MetadataParsing.parseDisplayName(it, berlin, now))
        }
        assertEquals(
            Instant.parse("2026-01-05T11:00:00Z").toEpochMilli(),
            MetadataParsing.parseDisplayName("IMG-20260105-WA0001.jpg", berlin, now),
        )
    }

    @Test fun filenamesRejectInvalidDatesTimesAndLooseMatches() {
        listOf(
            null, "", "renamed.jpg", "holiday20261016.jpg", "IMG_20260230_143215.jpg",
            "PXL_20261316_143215.jpg", "VID_20261016_243215.mp4", "IMG_20261016_146015.jpg",
            "2026-02-30 14.32.15.jpg", "IMG-20260229-WA0001.jpg", "IMG_19700101.jpg",
            "prefix_IMG_20261016_143215.jpg", "IMG_20261016_143215_extra.jpg",
            "20261016.jpg", "20261016", "VID-20261016-WA001.mp4", "VID-20261016-WA00001.mp4",
        ).forEach { assertNull(it, MetadataParsing.parseDisplayName(it, berlin, now)) }
        assertEquals(
            Instant.parse("2024-02-29T11:00:00Z").toEpochMilli(),
            MetadataParsing.parseDisplayName("IMG-20240229-WA0001.jpg", berlin, now),
        )
    }

    @Test fun filenameDatesAllowAtMostOneDayInTheFuture() {
        val boundary = now + 86_400_000L
        assertEquals(boundary, MetadataParsing.parseDisplayName("IMG_20261017_200000.jpg", berlin, now))
        listOf("IMG_20261017_200001.jpg", "IMG_20261018.jpg", "IMG_20991231.jpg", "IMG_20991231").forEach {
            assertNull(it, MetadataParsing.parseDisplayName(it, berlin, now))
        }
        val noon = Instant.parse("2026-10-16T10:00:00Z").toEpochMilli()
        assertEquals(noon + 86_400_000L, MetadataParsing.parseDisplayName("IMG_20261017.jpg", berlin, noon))
        assertNull(MetadataParsing.parseDisplayName("IMG_20261017.jpg", berlin, noon - 1))
    }

    @Test fun bareOrFutureFilenameDatesFallBackInsteadOfFilingUnderTheirDate() {
        val modified = now - 1_000
        listOf("20261016.jpg", "IMG_20991231.jpg").forEach { name ->
            assertEquals(
                MetadataParsing.ImportDate(modified, false),
                MetadataParsing.importDate(null, emptyList(), name, modified, { now }, berlin),
            )
            assertEquals(
                MetadataParsing.ImportDate(now, true),
                MetadataParsing.importDate(null, emptyList(), name, null, { now }, berlin),
            )
        }
    }

    @Test fun dateFallbackPrecedenceAndGuessFlag() {
        val embedded = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli()
        val provider = embedded + 1_000
        val modified = embedded + 2_000
        fun resolve(meta: Long?, taken: List<Long>, name: String?, last: Long?) =
            MetadataParsing.importDate(meta, taken, name, last, { now }, berlin)

        assertEquals(MetadataParsing.ImportDate(embedded, false), resolve(embedded, listOf(provider), "PXL_20261016_143215.jpg", modified))
        assertEquals(MetadataParsing.ImportDate(provider, false), resolve(null, listOf(provider, modified), "PXL_20261016_143215.jpg", modified))
        assertEquals(MetadataParsing.ImportDate(provider, false), resolve(null, listOf(0, provider / 1000), "PXL_20261016_143215.jpg", modified))
        assertEquals(
            MetadataParsing.ImportDate(Instant.parse("2026-10-16T12:32:15Z").toEpochMilli(), false),
            resolve(null, listOf(0), "PXL_20261016_143215.jpg", modified),
        )
        assertEquals(MetadataParsing.ImportDate(modified, false), resolve(null, emptyList(), "renamed.jpg", modified / 1000))
        assertEquals(MetadataParsing.ImportDate(now, true), resolve(null, listOf(0, -1), "renamed.jpg", 0))
    }

    @Test fun nullIslandIsRejectedForExifAndVideoButEquatorAndMeridianAreReal() {
        assertNull(MetadataParsing.validLocation(0.0, 0.0))
        assertNull(MetadataParsing.validLocation(-0.0, 0.0))
        assertNull(MetadataParsing.parseIso6709("+00.0000-000.0000/"))
        assertNull(MetadataParsing.parseIso6709("+0.0+0.0+012.5/"))
        assertEquals(0.0 to 12.0, MetadataParsing.validLocation(0.0, 12.0))
        assertEquals(12.0 to 0.0, MetadataParsing.validLocation(12.0, 0.0))
        assertEquals(0.0 to 12.0, MetadataParsing.parseIso6709("+0.0+12.0/"))
        assertEquals(12.0 to 0.0, MetadataParsing.parseIso6709("+12.0+0.0/"))
        assertNull(MetadataParsing.validLocation(null, 12.0))
        assertNull(MetadataParsing.validLocation(12.0, null))
        assertNull(MetadataParsing.validLocation(Double.NaN, 12.0))
        assertNull(MetadataParsing.validLocation(12.0, Double.POSITIVE_INFINITY))
        assertNull(MetadataParsing.validLocation(91.0, 12.0))
    }
}
