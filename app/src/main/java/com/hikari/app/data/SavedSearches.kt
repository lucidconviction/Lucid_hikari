package com.hikari.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * F6 — one folder of saved searches.
 *
 * [id] is stable across renames (the UI keys off it); [searches] is the
 * ordered list of queries the user saved into this folder (a search may live
 * in more than one folder — that is a feature, not a bug: "sports" and
 * "football" can both hold "nba final").
 */
data class SavedSearchFolder(
    val id: String,
    val name: String,
    val searches: List<String> = emptyList(),
)

/**
 * F6 — persistence for the saved-search folder tree.
 *
 * The whole tree is ONE JSON blob in DataStore (key `search_folders`, see
 * [AppStore.searchFoldersFlow]): one read, one write, and the existing
 * [AppStore.snapshotPreferences] flow backs it up like every other
 * preference. The JSON shape is deliberately flat and boring:
 *
 * ```
 * [{"id":"f1","name":"Sports","searches":["nba","f1 2026"]}, ...]
 * ```
 *
 * All the user-facing rules (a search in several folders, a deleted folder
 * dumping its entries into "General") are applied here, in the two
 * transformation helpers below, so the UI layer never touches JSON.
 */
object SavedSearchStore {

    const val GENERAL_NAME = "General"

    /** Parses the stored blob; tolerant of anything (corrupt JSON → empty). */
    fun parse(raw: String?): List<SavedSearchFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name").trim()
                    if (name.isEmpty()) continue
                    val searches = buildList {
                        val s = o.optJSONArray("searches") ?: continue
                        for (j in 0 until s.length()) {
                            val q = s.optString(j).trim()
                            if (q.isNotEmpty()) add(q)
                        }
                    }
                    add(SavedSearchFolder(o.optString("id").ifBlank { "f$i" }, name, searches))
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Serialises [folders] back to the blob shape above. */
    fun encode(folders: List<SavedSearchFolder>): String {
        val arr = JSONArray()
        folders.forEach { f ->
            val a = JSONArray()
            f.searches.forEach { a.put(it) }
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("searches", a))
        }
        return arr.toString()
    }

    /** A fresh, unique-ish folder id (same spirit as [AppStore.newId]). */
    fun newFolderId(): String = "f" + java.lang.Long.toHexString(System.currentTimeMillis()) +
        java.util.UUID.randomUUID().toString().take(4)

    /**
     * Saves [query] into [folderId] (or into the first folder that matches
     * [GENERAL_NAME] when [folderId] is null). Re-saving an existing query in
     * the same folder is a no-op; the query moves to the top of that folder's
     * list so the dropdown shows the most recent first.
     */
    fun saveCurrent(
        folders: List<SavedSearchFolder>,
        query: String,
        folderId: String?,
    ): List<SavedSearchFolder> {
        val q = query.trim()
        if (q.length < 2) return folders
        val targetId = folderId ?: folders.firstOrNull { it.name.equals(GENERAL_NAME, true) }?.id
        if (targetId == null) {
            // No folder to save into yet: create "General" and save there.
            val general = SavedSearchFolder(newFolderId(), GENERAL_NAME, listOf(q))
            return folders + general
        }
        return folders.map { f ->
            if (f.id != targetId) f else f.copy(
                searches = (listOf(q) + f.searches.filterNot { it.equals(q, true) }).distinct(),
            )
        }
    }

    /** Renames a folder in place. */
    fun renameFolder(folders: List<SavedSearchFolder>, folderId: String, name: String): List<SavedSearchFolder> =
        folders.map { if (it.id == folderId) it.copy(name = name.trim().ifBlank { it.name }) else it }

    /**
     * Deletes a folder. Its entries are not lost: they land in an existing
     * "General" folder, or a new one is created for them (see [GENERAL_NAME]).
     * Entries the folder shared with another folder are deduplicated there.
     */
    fun deleteFolder(folders: List<SavedSearchFolder>, folderId: String): List<SavedSearchFolder> {
        val victim = folders.firstOrNull { it.id == folderId } ?: return folders
        val rest = folders.filter { it.id != folderId }
        val orphans = victim.searches
        if (orphans.isEmpty()) return rest
        return when (val general = rest.firstOrNull { it.name.equals(GENERAL_NAME, true) }) {
            null -> rest + SavedSearchFolder(newFolderId(), GENERAL_NAME, orphans)
            else -> rest.map {
                if (it.id == general.id) {
                    it.copy(searches = (it.searches + orphans).distinct())
                } else it
            }
        }
    }

    /** Adds a new (empty) folder with [name]. */
    fun newFolder(folders: List<SavedSearchFolder>, name: String): List<SavedSearchFolder> {
        val n = name.trim()
        if (n.isEmpty()) return folders
        return folders + SavedSearchFolder(newFolderId(), n)
    }

    /**
     * Moves [query] from [fromId] to [toId]. The query stays in any OTHER
     * folder it lives in (a search may be saved into more than one folder).
     */
    fun moveToFolder(
        folders: List<SavedSearchFolder>,
        folderId: String,
        query: String,
        toFolderId: String,
    ): List<SavedSearchFolder> = folders.map { f ->
        when (f.id) {
            folderId -> f.copy(searches = f.searches.filterNot { it.equals(query, true) })
            toFolderId -> if (f.searches.any { it.equals(query, true) }) f
                else f.copy(searches = listOf(query) + f.searches)
            else -> f
        }
    }

    /** Removes one entry from one folder (it may remain in others). */
    fun removeEntry(
        folders: List<SavedSearchFolder>,
        folderId: String,
        query: String,
    ): List<SavedSearchFolder> = folders.map { f ->
        if (f.id == folderId) f.copy(searches = f.searches.filterNot { it.equals(query, true) }) else f
    }

    /** Reorders: moves [folderId] [delta] positions (clamped at the ends). */
    fun reorder(folders: List<SavedSearchFolder>, folderId: String, delta: Int): List<SavedSearchFolder> {
        val list = folders.toMutableList()
        val from = list.indexOfFirst { it.id == folderId }
        if (from < 0) return folders
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return folders
        list.add(to, list.removeAt(from))
        return list
    }
}
