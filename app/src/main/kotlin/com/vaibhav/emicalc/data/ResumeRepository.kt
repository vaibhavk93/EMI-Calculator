package com.vaibhav.emicalc.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaibhav.emicalc.core.session.InteractionKind
import com.vaibhav.emicalc.core.session.ResumePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

private val Context.resumeDataStore: DataStoreDelegate by preferencesDataStore(name = "resume")

private typealias DataStoreDelegate = androidx.datastore.core.DataStore<Preferences>

@Serializable
private data class ResumeDto(
    val kind: String,
    val route: String,
    val draft: String,
    val entryId: String?,
    val savedAtEpochMilli: Long,
    val focusField: String?,
)

/**
 * Persists where the user had got to in each flow.
 *
 * Drafts are small and are written on every pause, so DataStore is a better fit than
 * Room here: no schema, no migration, and an atomic whole-file write.
 */
class ResumeRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observe(kind: InteractionKind): Flow<ResumePoint?> =
        context.resumeDataStore.data.map { prefs -> prefs[key(kind)]?.let(::decode) }

    suspend fun latest(): ResumePoint? {
        val prefs = context.resumeDataStore.data.first()
        return InteractionKind.entries
            .mapNotNull { prefs[key(it)]?.let(::decode) }
            .maxByOrNull { it.savedAt }
    }

    suspend fun latestFor(kind: InteractionKind): ResumePoint? =
        context.resumeDataStore.data.first()[key(kind)]?.let(::decode)

    suspend fun save(point: ResumePoint) {
        context.resumeDataStore.edit { prefs ->
            prefs[key(point.kind)] = json.encodeToString(
                ResumeDto(
                    kind = point.kind.name,
                    route = point.route,
                    draft = point.draft,
                    entryId = point.entryId,
                    savedAtEpochMilli = point.savedAt.toEpochMilli(),
                    focusField = point.focusField,
                ),
            )
        }
    }

    suspend fun clear(kind: InteractionKind?) {
        context.resumeDataStore.edit { prefs ->
            if (kind == null) InteractionKind.entries.forEach { prefs.remove(key(it)) } else prefs.remove(key(kind))
        }
    }

    private fun key(kind: InteractionKind) = stringPreferencesKey("resume_${kind.name}")

    private fun decode(raw: String): ResumePoint? = runCatching {
        val dto = json.decodeFromString<ResumeDto>(raw)
        ResumePoint(
            kind = InteractionKind.valueOf(dto.kind),
            route = dto.route,
            draft = dto.draft,
            entryId = dto.entryId,
            savedAt = Instant.ofEpochMilli(dto.savedAtEpochMilli),
            focusField = dto.focusField,
        )
    }.getOrNull()
}
