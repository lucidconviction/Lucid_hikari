package com.hikari.app.web.market

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MarketState(
    val catalog: MarketCatalog? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** Non-null means the last refresh failed; the catalogue is still usable. */
    val error: String? = null,
)

/**
 * The market's one source of truth.
 *
 * The first open reads the bundled SFW seed instantly. A refresh is attempted
 * only when that catalogue is older than a week (or when the user asks), and a
 * failed source never empties a category: the previous list stays on screen
 * until a later refresh returns something. Adult categories are fetched only
 * when the NSFW switch is on, and never while it is off.
 */
object WebAppMarketRepo {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(MarketState())
    val state: StateFlow<MarketState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    /** Guards against two screens or a manual + automatic refresh racing. */
    private var refreshing = false

    fun ensureLoaded(context: Context) {
        scope.launch { load(context) }
    }

    suspend fun load(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            _state.value = _state.value.copy(loading = true, error = null)
            val catalog = runCatching { MarketStore.load(context) }.getOrElse { MarketCatalog() }
            _state.value = MarketState(catalog = catalog, loading = false)
            loaded = true
        }
    }

    /** Fire-and-forget refresh for the screen's `LaunchedEffect`. */
    fun refresh(
        context: Context,
        includeFmhy: Boolean,
        includeAdult: Boolean,
        force: Boolean = false,
    ) {
        scope.launch { refreshNow(context, includeFmhy, includeAdult, force) }
    }

    /** Adult-only refresh when the Adult group is first opened. */
    fun refreshAdult(context: Context) {
        scope.launch { refreshNow(context, includeFmhy = false, includeAdult = true, force = true) }
    }

    suspend fun refreshNow(
        context: Context,
        includeFmhy: Boolean,
        includeAdult: Boolean,
        force: Boolean,
    ) {
        load(context)
        mutex.withLock {
            val current = _state.value.catalog ?: MarketCatalog()
            if (!force && !isStale(current)) return
            if (refreshing) return
            refreshing = true
            _state.value = _state.value.copy(refreshing = true, error = null)
            try {
                val fmhy = if (includeFmhy) {
                    runCatching { FmhyMarketSource.fetch() }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val adult = if (includeAdult) fetchAdult() else emptyMap()
                val gotFmhy = includeFmhy && fmhy.isNotEmpty()
                val gotAdult = includeAdult && adult.values.any { it.isNotEmpty() }
                // Do not reset the seven-day timer on a refresh that produced
                // nothing: the next open should try again, not wait a week on an
                // empty response.
                if ((includeFmhy || includeAdult) && !gotFmhy && !gotAdult) {
                    _state.value = _state.value.copy(
                        refreshing = false,
                        error = "refresh_empty",
                    )
                    return
                }
                val partial = (includeFmhy && !gotFmhy) || (includeAdult && !gotAdult)
                val merged = merge(current, fmhy, adult)
                val next = MarketCatalog(
                    schema = MarketCatalog.SCHEMA,
                    fetchedAt = System.currentTimeMillis(),
                    apps = merged,
                )
                MarketStore.save(context, next)
                _state.value = MarketState(
                    catalog = next,
                    loading = false,
                    error = if (partial) "refresh_partial" else null,
                )
            } catch (e: Exception) {
                // Keep the old catalogue; the banner is the only change.
                _state.value = _state.value.copy(
                    refreshing = false,
                    error = e.message ?: "refresh_failed",
                )
            } finally {
                refreshing = false
            }
        }
    }

    internal fun isStale(catalog: MarketCatalog): Boolean =
        catalog.fetchedAt <= 0L ||
            System.currentTimeMillis() - catalog.fetchedAt > MarketCatalog.SEVEN_DAYS_MS

    /**
     * Replaces a category only when the new source returned something. This is
     * the difference between "FMHY is down" and "the market is empty": a bad
     * fetch must not erase a tab the user could still use.
     */
    private fun merge(
        current: MarketCatalog,
        fmhy: List<MarketApp>,
        adult: Map<String, List<MarketApp>>,
    ): List<MarketApp> {
        val byCategory = LinkedHashMap<String, List<MarketApp>>()
        for (category in MarketCategories.all) {
            val fresh = if (category.adult) {
                adult[category.id]
            } else {
                fmhy.filter { it.categoryId == category.id }
            }
            val old = current.apps.filter { it.categoryId == category.id }
            val chosen = when {
                fresh != null && fresh.isNotEmpty() -> fresh
                old.isNotEmpty() -> old
                else -> emptyList()
            }
            if (chosen.isNotEmpty()) {
                byCategory[category.id] = chosen
                    .take(category.cap)
                    .mapIndexed { index, app -> app.copy(rank = index + 1) }
            }
        }
        return byCategory.values.flatten()
    }

    private suspend fun fetchAdult(): Map<String, List<MarketApp>> = coroutineScope {
        MarketCategories.adult.map { category ->
            async(Dispatchers.IO) {
                category.id to runCatching {
                    ThePornDudeMarketSource.fetchCategory(category)
                }.getOrDefault(emptyList())
            }
        }.awaitAll().toMap()
    }

    /** Test hook: forget the in-memory catalogue without touching disk. */
    internal fun resetForTests() {
        loaded = false
        refreshing = false
        _state.value = MarketState()
    }
}
