package com.vaibhav.emicalc.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit

class HistoryTest {

    private lateinit var store: InMemoryHistoryStore
    private val t0: Instant = Instant.parse("2026-10-05T10:00:00Z")

    @BeforeEach
    fun setUp() {
        store = InMemoryHistoryStore()
    }

    private fun entry(
        id: String,
        kind: InteractionKind = InteractionKind.CALCULATION,
        at: Instant = t0,
    ) = HistoryEntry(
        id = id,
        kind = kind,
        createdAt = at,
        title = "1200*5%",
        summary = "60",
        payload = """{"expr":"1200*5%"}""",
    )

    @Test
    @DisplayName("entries are remembered and listed most recent first")
    fun recordsAndOrders() {
        store.record(entry("a", at = t0))
        store.record(entry("b", at = t0.plus(1, ChronoUnit.HOURS)))
        assertEquals(listOf("b", "a"), store.list().map { it.id })
    }

    @Test
    @DisplayName("history can be filtered to one kind of interaction")
    fun filtersByKind() {
        store.record(entry("calc", InteractionKind.CALCULATION))
        store.record(entry("loan", InteractionKind.LOAN))
        store.record(entry("fnf", InteractionKind.SETTLEMENT))
        assertEquals(listOf("loan"), store.list(InteractionKind.LOAN).map { it.id })
        assertEquals(3, store.list().size)
    }

    @Test
    @DisplayName("a note can be added, replaced and removed")
    fun notes() {
        store.record(entry("a"))
        assertNull(store.find("a")?.note)

        val annotated = store.annotate("a", "This is my gross salary", t0)
        assertEquals("This is my gross salary", annotated?.note?.text)
        assertEquals(t0, annotated?.note?.updatedAt)

        val later = t0.plus(5, ChronoUnit.MINUTES)
        assertEquals("Corrected figure", store.annotate("a", "Corrected figure", later)?.note?.text)

        assertNull(store.annotate("a", null, later)?.note, "null clears the note")
        assertNull(store.annotate("a", "   ", later)?.note, "a blank note is treated as none")
    }

    @Test
    fun annotatingAMissingEntryReturnsNull() {
        assertNull(store.annotate("nope", "x", t0))
        assertNull(store.delete("nope", t0))
        assertNull(store.restore("nope"))
        assertFalse(store.purge("nope"))
    }

    @Test
    fun notesAreLengthLimited() {
        assertThrows<IllegalArgumentException> { Note("x".repeat(Note.MAX_LENGTH + 1), t0) }
    }

    @Test
    @DisplayName("deletion hides an entry but is reversible")
    fun deleteIsReversible() {
        store.record(entry("a"))
        store.delete("a", t0)

        assertTrue(store.list().isEmpty(), "deleted entries are hidden by default")
        assertEquals(1, store.list(includeDeleted = true).size)
        assertTrue(store.find("a")!!.isDeleted)

        store.restore("a")
        assertEquals(1, store.list().size)
        assertFalse(store.find("a")!!.isDeleted)
    }

    @Test
    @DisplayName("purging is irreversible and actually frees the entry")
    fun purge() {
        store.record(entry("a"))
        assertTrue(store.purge("a"))
        assertNull(store.find("a"))
        assertTrue(store.list(includeDeleted = true).isEmpty())
    }

    @Test
    @DisplayName("pinning protects an entry from delete and clear, and floats it to the top")
    fun pinningProtects() {
        store.record(entry("old", at = t0))
        store.record(entry("new", at = t0.plus(1, ChronoUnit.HOURS)))
        store.setPinned("old", true)

        assertEquals(listOf("old", "new"), store.list().map { it.id }, "pinned entries sort first")

        store.delete("old", t0)
        assertFalse(store.find("old")!!.isDeleted, "a pinned entry resists deletion")

        store.clear(at = t0)
        assertEquals(listOf("old"), store.list().map { it.id }, "clear spares pinned entries")
    }

    @Test
    @DisplayName("clear can be scoped to one kind")
    fun clearByKind() {
        store.record(entry("calc", InteractionKind.CALCULATION))
        store.record(entry("loan", InteractionKind.LOAN))
        assertEquals(1, store.clear(InteractionKind.CALCULATION, t0))
        assertEquals(listOf("loan"), store.list().map { it.id })
    }

    @Test
    @DisplayName("old deleted entries are purged on a retention sweep, recent ones survive")
    fun retentionSweep() {
        store.record(entry("stale"))
        store.record(entry("fresh"))
        store.delete("stale", t0)
        store.delete("fresh", t0.plus(40, ChronoUnit.DAYS))

        assertEquals(1, store.purgeDeletedBefore(t0.plus(30, ChronoUnit.DAYS)))
        assertNull(store.find("stale"))
        assertNotNull(store.find("fresh"))
    }

    @Test
    @DisplayName("deleting twice does not move the deletion timestamp")
    fun deleteIsIdempotent() {
        store.record(entry("a"))
        store.delete("a", t0)
        val later = t0.plus(1, ChronoUnit.HOURS)
        assertEquals(t0, store.delete("a", later)?.deletedAt)
    }
}
