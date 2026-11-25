package com.hikari.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One playlist group's local tweak: a display label, an explicit position, a
 * hidden flag and a lock.
 *
 * The original `group-title` is the stable key and is never rewritten: catalog
 * IDs (and therefore channel lists, search and deep links) still use the
 * original name. Only the tile the user sees changes.
 */
@Serializable
data class IptvGroupOverride(
    /** Original `group-title` from the playlist. */
    val name: String,
    /** Optional display-only label; blank means "use [name]". */
    val label: String = "",
    /** Lower shows first. 0 means "not positioned", i.e. after the ordered ones. */
    val order: Int = 0,
    /** Hide only the group tile; its channels stay in All channels and search. */
    val hidden: Boolean = false,
    /** Lock protects the tile's own rename/move/hide actions, not playback. */
    val locked: Boolean = false,
) {
    fun displayName(): String = label.ifBlank { name }
}

/**
 * JSON helpers for the per-playlist group overrides stored by [AppStore].
 *
 * The value is a map of provider id → override list. A missing key, an empty
 * string or malformed JSON all read as "no overrides", so an upgrade (or a
 * corrupted preference) can never hide a playlist.
 */
object IptvGroupPrefs {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(
        String.serializer(),
        ListSerializer(IptvGroupOverride.serializer()),
    )

    fun decode(raw: String?): Map<String, List<IptvGroupOverride>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyMap())
            .mapValues { (_, list) ->
                list.filter { it.name.isNotBlank() }
                    .distinctBy { it.name }
            }
            .filterValues { it.isNotEmpty() }
    }

    fun encode(value: Map<String, List<IptvGroupOverride>>): String {
        val clean = value.mapValues { (_, list) ->
            list.filter { it.name.isNotBlank() }.distinctBy { it.name }
        }.filterValues { it.isNotEmpty() }
        return json.encodeToString(
            serializer,
            clean.toSortedMap(),
        )
    }

    fun forProvider(raw: String?, providerId: String): List<IptvGroupOverride> =
        decode(raw)[providerId].orEmpty()

    /** Parse, apply [transform] to this provider's list, and serialise back. */
    fun update(
        raw: String?,
        providerId: String,
        transform: (List<IptvGroupOverride>) -> List<IptvGroupOverride>,
    ): String {
        val map = decode(raw).toMutableMap()
        val next = transform(map[providerId].orEmpty())
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name }
        if (next.isEmpty()) map.remove(providerId) else map[providerId] = next
        return encode(map)
    }

    fun upsert(raw: String?, providerId: String, override: IptvGroupOverride): String =
        update(raw, providerId) { list ->
            list.filterNot { it.name == override.name } + override
        }

    fun remove(raw: String?, providerId: String, name: String): String =
        update(raw, providerId) { list -> list.filterNot { it.name == name } }

    fun find(overrides: List<IptvGroupOverride>, name: String): IptvGroupOverride? =
        overrides.firstOrNull { it.name == name }

    fun isLocked(overrides: List<IptvGroupOverride>, name: String): Boolean =
        find(overrides, name)?.locked == true

    fun isHidden(overrides: List<IptvGroupOverride>, name: String): Boolean =
        find(overrides, name)?.hidden == true

    fun labelOf(overrides: List<IptvGroupOverride>, name: String): String =
        find(overrides, name)?.displayName() ?: name

    /**
     * Apply the overrides to a group list in the order the playlist produced:
     * drop hidden names, then move explicitly ordered names to the front in
     * ascending order. Unpositioned names keep their original (biggest-first)
     * order behind them.
     */
    fun arrange(names: List<String>, overrides: List<IptvGroupOverride>): List<String> {
        if (overrides.isEmpty()) return names
        val byName = overrides.associateBy { it.name }
        val visible = names.filterNot { byName[it]?.hidden == true }
        if (visible.isEmpty()) return emptyList()
        val index = visible.withIndex().associate { (i, n) -> n to i }
        return visible.sortedWith(
            compareBy(
                { n -> byName[n]?.order?.takeIf { it > 0 } ?: Int.MAX_VALUE },
                { n -> index[n] ?: Int.MAX_VALUE },
            ),
        )
    }

    /** The next order value after every explicit one; 1 when none is set. */
    fun nextOrder(overrides: List<IptvGroupOverride>): Int =
        (overrides.maxOfOrNull { it.order } ?: 0).coerceAtLeast(0) + 1

    /**
     * Pin the visible groups to [orderedNames] by assigning order 1..N. Other
     * fields (label/hidden/locked) are kept, and overrides for names that are
     * not visible (e.g. the hidden ones) are untouched.
     */
    fun withOrder(
        raw: String?,
        providerId: String,
        orderedNames: List<String>,
    ): String = update(raw, providerId) { list ->
        val byName = list.associateBy { it.name }.toMutableMap()
        orderedNames.forEachIndexed { i, name ->
            if (name.isBlank()) return@forEachIndexed
            val current = byName[name] ?: IptvGroupOverride(name)
            byName[name] = current.copy(order = i + 1)
        }
        byName.values.toList()
    }

    /**
     * Swap [name] with its neighbour in [orderedNames] and persist the new
     * order. Returns the raw value unchanged when the move is impossible.
     */
    fun move(
        raw: String?,
        providerId: String,
        orderedNames: List<String>,
        name: String,
        delta: Int,
    ): String {
        val from = orderedNames.indexOf(name)
        if (from < 0) return encode(decode(raw))
        val to = from + delta
        if (to !in orderedNames.indices) return encode(decode(raw))
        val next = orderedNames.toMutableList()
        val moved = next.removeAt(from)
        next.add(to, moved)
        return withOrder(raw, providerId, next)
    }
}
