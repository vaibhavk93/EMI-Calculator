package com.vaibhav.emicalc.core.session

import java.time.Instant

/**
 * Where the user had got to, so the app reopens there instead of at a blank screen.
 *
 * [draft] is serialised, partly-filled form state, which matters most for the settlement
 * flow: it is long, people fill it in over several sittings, and an app that forgets
 * halfway is an app they stop using. [entryId] is set when the draft is an edit of
 * something already in history rather than something new.
 */
data class ResumePoint(
    val kind: InteractionKind,
    val route: String,
    val draft: String,
    val entryId: String? = null,
    val savedAt: Instant,
    /** Where the caret or scroll position was, so the screen reopens in place. */
    val focusField: String? = null,
)

interface ResumeStore {
    fun save(point: ResumePoint)

    /** The most recently saved point, or null on a clean start. */
    fun latest(): ResumePoint?

    fun latestFor(kind: InteractionKind): ResumePoint?

    /** Called once the draft has been committed to history or abandoned. */
    fun clear(kind: InteractionKind? = null)
}

/** In-memory [ResumeStore]; the app module persists via DataStore. */
class InMemoryResumeStore : ResumeStore {

    private val byKind = LinkedHashMap<InteractionKind, ResumePoint>()

    override fun save(point: ResumePoint) {
        byKind[point.kind] = point
    }

    override fun latest(): ResumePoint? = byKind.values.maxByOrNull { it.savedAt }

    override fun latestFor(kind: InteractionKind): ResumePoint? = byKind[kind]

    override fun clear(kind: InteractionKind?) {
        if (kind == null) byKind.clear() else byKind.remove(kind)
    }
}
