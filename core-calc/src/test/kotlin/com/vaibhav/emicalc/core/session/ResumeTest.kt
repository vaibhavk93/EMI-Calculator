package com.vaibhav.emicalc.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ResumeTest {

    private val t0: Instant = Instant.parse("2026-10-05T10:00:00Z")

    private fun point(kind: InteractionKind, at: Instant, draft: String = "{}") =
        ResumePoint(kind = kind, route = kind.name.lowercase(), draft = draft, savedAt = at)

    @Test
    @DisplayName("a clean start has nothing to resume")
    fun cleanStart() {
        assertNull(InMemoryResumeStore().latest())
        assertNull(InMemoryResumeStore().latestFor(InteractionKind.SETTLEMENT))
    }

    @Test
    @DisplayName("the app resumes at the most recently touched flow")
    fun resumesMostRecent() {
        val store = InMemoryResumeStore()
        store.save(point(InteractionKind.LOAN, t0))
        store.save(point(InteractionKind.SETTLEMENT, t0.plus(10, ChronoUnit.MINUTES)))
        assertEquals(InteractionKind.SETTLEMENT, store.latest()?.kind)
    }

    @Test
    @DisplayName("each flow keeps its own draft, so switching tabs loses nothing")
    fun draftsAreIndependent() {
        val store = InMemoryResumeStore()
        store.save(point(InteractionKind.LOAN, t0, """{"principal":"8000000"}"""))
        store.save(point(InteractionKind.SETTLEMENT, t0.plusSeconds(60), """{"basic":"80000"}"""))

        assertEquals("""{"principal":"8000000"}""", store.latestFor(InteractionKind.LOAN)?.draft)
        assertEquals("""{"basic":"80000"}""", store.latestFor(InteractionKind.SETTLEMENT)?.draft)
    }

    @Test
    @DisplayName("saving again overwrites that flow's draft rather than stacking")
    fun savingOverwrites() {
        val store = InMemoryResumeStore()
        store.save(point(InteractionKind.SETTLEMENT, t0, """{"step":1}"""))
        store.save(point(InteractionKind.SETTLEMENT, t0.plusSeconds(60), """{"step":2}"""))
        assertEquals("""{"step":2}""", store.latestFor(InteractionKind.SETTLEMENT)?.draft)
    }

    @Test
    @DisplayName("committing a draft clears just that flow, or all of them")
    fun clearing() {
        val store = InMemoryResumeStore()
        store.save(point(InteractionKind.LOAN, t0))
        store.save(point(InteractionKind.SETTLEMENT, t0.plusSeconds(60)))

        store.clear(InteractionKind.SETTLEMENT)
        assertNull(store.latestFor(InteractionKind.SETTLEMENT))
        assertEquals(InteractionKind.LOAN, store.latest()?.kind)

        store.clear()
        assertNull(store.latest())
    }

    @Test
    @DisplayName("a resume point can point back at the history entry it is editing")
    fun editingAnExistingEntry() {
        val store = InMemoryResumeStore()
        store.save(
            ResumePoint(
                kind = InteractionKind.SETTLEMENT,
                route = "settlement/edit",
                draft = """{"basic":"90000"}""",
                entryId = "entry-42",
                savedAt = t0,
                focusField = "earnedLeaveBalanceDays",
            ),
        )
        val resumed = store.latestFor(InteractionKind.SETTLEMENT)
        assertEquals("entry-42", resumed?.entryId)
        assertEquals("earnedLeaveBalanceDays", resumed?.focusField)
    }
}
