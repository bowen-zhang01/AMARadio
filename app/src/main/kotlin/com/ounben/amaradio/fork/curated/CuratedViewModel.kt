package com.ounben.amaradio.fork.curated

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.ounben.amaradio.AMARadioApp
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.database.user.AMARadioUserDatabase
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CuratedSourceState(
    val source: CuratedSource,
    val playlist: CuratedPlaylist? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CuratedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AMARadioApp
    private val repository = CuratedRepository(application) { app.httpClient }

    private val _sources = MutableStateFlow(CuratedSources.all.associate { it.id to CuratedSourceState(it) })
    val sources: StateFlow<Map<String, CuratedSourceState>> = _sources.asStateFlow()

    /** Most-played stations outside China in the local radio-browser catalog. */
    private val _popular = MutableStateFlow<List<DataRadioStation>>(emptyList())
    val popular: StateFlow<List<DataRadioStation>> = _popular.asStateFlow()

    init {
        viewModelScope.launch { seedFavouritesOnFirstRun() }
        viewModelScope.launch { _popular.value = loadPopularWorldwide() }
        load(CuratedSources.beijingNational)
    }

    private suspend fun loadPopularWorldwide(): List<DataRadioStation> = withContext(Dispatchers.IO) {
        runCatching {
            AMARadioDatabase.getDatabase(app).stationDao()
                .getStationsFiltered(null, null, null, null, null, null, "clickcount", 1)
                .asSequence()
                .map { it.toDataStation() }
                .filter { it.CountryCode != "CN" && it.IconUrl.startsWith("http") }
                .distinctBy { it.Name.trim().lowercase() }
                .take(POPULAR_COUNT)
                .toList()
        }.getOrDefault(emptyList())
    }

    fun load(source: CuratedSource, forceRefresh: Boolean = false) {
        val current = _sources.value[source.id] ?: return
        if (current.isLoading || (!forceRefresh && current.playlist != null)) return
        updateSource(source.id) { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Show the copy shipped in the APK at once; the refresh below replaces it if newer.
            if (current.playlist == null && source.isBundled) {
                val bundled = withContext(Dispatchers.IO) { runCatching { repository.loadBundled(source) }.getOrNull() }
                if (bundled != null) updateSource(source.id) { it.copy(playlist = bundled) }
            }
            repository.load(source, forceRefresh)
                .onSuccess { playlist -> updateSource(source.id) { it.copy(playlist = playlist, isLoading = false) } }
                .onFailure { e -> updateSource(source.id) { it.copy(isLoading = false, error = e.message ?: e.javaClass.simpleName) } }
        }
    }

    /** Adds every non-backup station of [source] that is not a favourite yet. */
    fun addAllToFavourites(source: CuratedSource): Int {
        val stations = _sources.value[source.id]?.playlist?.primaryStations.orEmpty()
            .filterNot { app.favouriteManager.has(it.StationUuid) }
        if (stations.isNotEmpty()) app.favouriteManager.addMultiple(stations)
        return stations.size
    }

    fun toggleFavourite(station: DataRadioStation) {
        if (app.favouriteManager.has(station.StationUuid)) app.favouriteManager.remove(station.StationUuid)
        else app.favouriteManager.add(station)
    }

    private fun updateSource(id: String, transform: (CuratedSourceState) -> CuratedSourceState) {
        _sources.update { map -> map[id]?.let { map + (id to transform(it)) } ?: map }
    }

    /**
     * A fresh install starts with the Beijing + national list as favourites, so widgets,
     * launcher shortcuts and Android Auto (which all read favourites) are useful at once.
     * Runs once; never touches an existing favourites list.
     */
    private suspend fun seedFavouritesOnFirstRun() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        if (prefs.getBoolean(PREF_FAVOURITES_SEEDED, false)) return
        val stations = withContext(Dispatchers.IO) {
            val hasFavourites = AMARadioUserDatabase.getDatabase(app).favoriteDao().getMaxOrder() != null
            if (hasFavourites) emptyList()
            else repository.loadBundled(CuratedSources.beijingNational)?.primaryStations.orEmpty()
        }
        if (stations.isNotEmpty()) app.favouriteManager.addMultiple(stations)
        prefs.edit { putBoolean(PREF_FAVOURITES_SEEDED, true) }
    }

    companion object {
        private const val PREF_FAVOURITES_SEEDED = "fork_curated_favourites_seeded_v1"
        private const val POPULAR_COUNT = 16
    }
}
