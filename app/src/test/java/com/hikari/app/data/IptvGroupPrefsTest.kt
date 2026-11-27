package com.hikari.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvGroupPrefsTest {

    private val names = listOf("News", "Sport", "Movies", "Kids")

    @Test
    fun noOverridesKeepsThePlaylistOrder() {
        assertEquals(names, IptvGroupPrefs.arrange(names, emptyList()))
    }

    @Test
    fun explicitOrderComesFirstAndUnorderedKeepTheirOrder() {
        val overrides = listOf(
            IptvGroupOverride("Movies", order = 1),
            IptvGroupOverride("News", order = 2),
        )
        assertEquals(
            listOf("Movies", "News", "Sport", "Kids"),
            IptvGroupPrefs.arrange(names, overrides),
        )
    }

    @Test
    fun hiddenGroupsAreFiltered() {
        val overrides = listOf(IptvGroupOverride("Sport", hidden = true))
        assertEquals(listOf("News", "Movies", "Kids"), IptvGroupPrefs.arrange(names, overrides))
    }

    @Test
    fun hiddenAndOrderedCombine() {
        val overrides = listOf(
            IptvGroupOverride("Kids", order = 1, hidden = true),
            IptvGroupOverride("Movies", order = 2),
        )
        assertEquals(
            listOf("Movies", "News", "Sport"),
            IptvGroupPrefs.arrange(names, overrides),
        )
    }

    @Test
    fun labelFallsBackToTheOriginalName() {
        val overrides = listOf(IptvGroupOverride("News", label = "World news"))
        assertEquals("World news", IptvGroupPrefs.labelOf(overrides, "News"))
        assertEquals("Sport", IptvGroupPrefs.labelOf(overrides, "Sport"))
    }

    @Test
    fun lockDoesNotAffectOrderingOrVisibility() {
        val overrides = listOf(IptvGroupOverride("Movies", order = 1, locked = true))
        assertTrue(IptvGroupPrefs.isLocked(overrides, "Movies"))
        assertEquals(
            listOf("Movies", "News", "Sport", "Kids"),
            IptvGroupPrefs.arrange(names, overrides),
        )
    }

    @Test
    fun serializationRoundTripsByProvider() {
        val raw = IptvGroupPrefs.update(null, "provider-a") { list ->
            list + IptvGroupOverride("News", label = "World", order = 1, locked = true)
        }
        val again = IptvGroupPrefs.update(raw, "provider-b") { list ->
            list + IptvGroupOverride("Sport", hidden = true)
        }
        val map = IptvGroupPrefs.decode(again)
        assertEquals(2, map.size)
        assertEquals("World", map["provider-a"]?.single()?.label)
        assertTrue(map["provider-a"]?.single()?.locked == true)
        assertTrue(map["provider-b"]?.single()?.hidden == true)
        assertTrue(IptvGroupPrefs.forProvider(again, "missing").isEmpty())
    }

    @Test
    fun malformedJsonReadsAsNoOverrides() {
        assertTrue(IptvGroupPrefs.decode("{not json").isEmpty())
        assertTrue(IptvGroupPrefs.decode(null).isEmpty())
        assertTrue(IptvGroupPrefs.decode("").isEmpty())
    }

    @Test
    fun restoringAHiddenGroupShowsItAgain() {
        var raw = IptvGroupPrefs.upsert(null, "p", IptvGroupOverride("Sport", hidden = true))
        assertFalse(IptvGroupPrefs.arrange(names, IptvGroupPrefs.forProvider(raw, "p")).contains("Sport"))
        raw = IptvGroupPrefs.upsert(raw, "p", IptvGroupOverride("Sport", hidden = false))
        assertEquals(names, IptvGroupPrefs.arrange(names, IptvGroupPrefs.forProvider(raw, "p")))
    }

    @Test
    fun moveSwapsWithTheNeighbourAndPersistsOrder() {
        val raw = IptvGroupPrefs.move(null, "p", names, "Sport", -1)
        val overrides = IptvGroupPrefs.forProvider(raw, "p")
        assertEquals(
            listOf("Sport", "News", "Movies", "Kids"),
            IptvGroupPrefs.arrange(names, overrides),
        )
        val movedDown = IptvGroupPrefs.move(raw, "p", IptvGroupPrefs.arrange(names, overrides), "Sport", 1)
        assertEquals(
            names,
            IptvGroupPrefs.arrange(names, IptvGroupPrefs.forProvider(movedDown, "p")),
        )
    }

    @Test
    fun moveAtTheEdgesIsANoOp() {
        val raw = IptvGroupPrefs.move(null, "p", names, "News", -1)
        assertNull(IptvGroupPrefs.forProvider(raw, "p").firstOrNull { it.order != 0 })
        val raw2 = IptvGroupPrefs.move(null, "p", names, "Kids", 1)
        assertTrue(IptvGroupPrefs.forProvider(raw2, "p").isEmpty())
    }

    @Test
    fun removingAnOverrideDropsTheProviderWhenEmpty() {
        val raw = IptvGroupPrefs.upsert(null, "p", IptvGroupOverride("News", hidden = true))
        val removed = IptvGroupPrefs.remove(raw, "p", "News")
        assertTrue(IptvGroupPrefs.decode(removed).isEmpty())
    }
}
