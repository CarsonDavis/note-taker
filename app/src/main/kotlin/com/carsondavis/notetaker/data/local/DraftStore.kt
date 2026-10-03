package com.carsondavis.notetaker.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.draftDataStore by preferencesDataStore(name = "note_draft")

/**
 * On-disk copy of the in-progress note (M51). The note text otherwise lives only in
 * [com.carsondavis.notetaker.ui.viewmodels.NoteViewModel] memory, so process death —
 * the OS reclaiming a backgrounded app, a crash, the task being swiped away — threw
 * away everything dictated so far. [NoteViewModel] restores the draft at startup,
 * writes every change on a short debounce, and clears it once the note is sent or
 * queued. "Delete all data" in Settings clears it too.
 */
@Singleton
class DraftStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val textKey = stringPreferencesKey("text")

    /** The saved draft, or "" when there is none (or the file is unreadable). */
    suspend fun load(): String = context.draftDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[textKey] ?: "" }
        .first()

    suspend fun save(text: String) {
        context.draftDataStore.edit { prefs ->
            if (text.isBlank()) prefs.remove(textKey) else prefs[textKey] = text
        }
    }

    suspend fun clear() = save("")
}
