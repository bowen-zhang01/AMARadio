package com.ounben.amaradio.fork.world

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ounben.amaradio.Utils
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.CountryCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CountryItem(
    val code: String,
    /** Name in the interface language. */
    val name: String,
    /** English name: shown under a localized name and matched by search. */
    val englishName: String,
    val stationCount: Int,
    val continent: Continent
)

data class WorldUiState(
    val isLoading: Boolean = true,
    /** Every country with working stations, most stations first. */
    val countries: List<CountryItem> = emptyList(),
    val totalStations: Int = 0,
    /** Device and language countries, then recently opened ones (all present in [countries]). */
    val shortcuts: List<CountryItem> = emptyList()
) {
    fun country(code: String): CountryItem? = countries.firstOrNull { it.code == code }

    /**
     * Countries whose localized name, English name or ISO code matches [query]: exact codes
     * and name prefixes first, then other matches, larger countries first within each group.
     */
    fun search(query: String): List<CountryItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return countries
            .mapNotNull { country ->
                val names = listOf(country.name, country.englishName)
                when {
                    country.code.equals(q, ignoreCase = true) -> 0
                    names.any { it.startsWith(q, ignoreCase = true) } -> 1
                    names.any { it.contains(q, ignoreCase = true) } -> 2
                    else -> null
                }?.let { rank -> rank to country }
            }
            .sortedWith(compareBy<Pair<Int, CountryItem>> { it.first }.thenByDescending { it.second.stationCount })
            .map { it.second }
    }
}

/** State of the World tab: the countries of the local radio-browser catalog. */
class WorldViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val _uiState = MutableStateFlow(WorldUiState())
    val uiState: StateFlow<WorldUiState> = _uiState.asStateFlow()

    private var counts: List<CountryCount> = emptyList()
    private var locale: Locale = application.resources.configuration.locales[0]
    private var loadedAt = 0L
    private var loadJob: Job? = null

    init {
        refresh()
    }

    /** Re-labels the countries when the interface language changes. */
    fun setLocale(newLocale: Locale) {
        if (newLocale == locale) return
        locale = newLocale
        if (loadedAt > 0) viewModelScope.launch { publish() }
    }

    /** The catalog syncs in the background, so counts are reloaded now and then. */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - loadedAt > STALE_AFTER_MS) refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            counts = withContext(Dispatchers.IO) {
                runCatching { AMARadioDatabase.getDatabase(getApplication()).stationDao().getCountryCounts() }
                    .getOrDefault(emptyList())
            }
            loadedAt = System.currentTimeMillis()
            publish()
        }
    }

    fun markVisited(code: String) {
        val recent = (listOf(code) + recentCodes()).distinct().take(MAX_RECENT)
        prefs.edit { putString(PREF_RECENT, recent.joinToString(",")) }
        _uiState.value = _uiState.value.let { it.copy(shortcuts = shortcutsFor(it.countries)) }
    }

    private suspend fun publish() {
        val locale = locale
        val countries = withContext(Dispatchers.Default) {
            // XX and ZZ stand for an unknown country.
            counts.filter { it.code.length == 2 && it.code != "XX" && it.code != "ZZ" }.map { count ->
                CountryItem(
                    code = count.code,
                    name = Countries.displayName(count.code, locale, count.name),
                    englishName = Countries.displayName(count.code, Locale.ENGLISH, count.name),
                    stationCount = count.count,
                    continent = Countries.continentOf(count.code)
                )
            }
        }
        _uiState.value = WorldUiState(
            isLoading = false,
            countries = countries,
            totalStations = countries.sumOf { it.stationCount },
            shortcuts = shortcutsFor(countries)
        )
    }

    private fun shortcutsFor(countries: List<CountryItem>): List<CountryItem> {
        val byCode = countries.associateBy { it.code }
        val codes = Countries.suggested(Utils.getCountryCode(getApplication()), locale) + recentCodes()
        return codes.distinct().mapNotNull { byCode[it] }.take(MAX_SHORTCUTS)
    }

    private fun recentCodes(): List<String> =
        prefs.getString(PREF_RECENT, null).orEmpty().split(',').filter { it.length == 2 }

    companion object {
        private const val PREF_RECENT = "fork_recent_countries"
        private const val MAX_RECENT = 6
        private const val MAX_SHORTCUTS = 8
        private val STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(30)
    }
}
