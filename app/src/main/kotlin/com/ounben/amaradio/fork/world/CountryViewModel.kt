package com.ounben.amaradio.fork.world

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CountryUiState(
    val isLoading: Boolean = true,
    /** Stations of the selected region, or of the whole country when none is selected. */
    val stations: List<DataRadioStation> = emptyList(),
    val regions: List<RegionFilter> = emptyList(),
    val selectedRegion: String? = null,
    /** True when the list stops at [CountryViewModel.MAX_STATIONS]; search finds the rest. */
    val isTruncated: Boolean = false
) {
    fun region(id: String?): RegionFilter? = regions.firstOrNull { it.id == id }
}

/**
 * One country page (route `country/{code}`), or one region of it (`country/{code}?region=…`):
 * its stations from the local catalog, most played first. [Countries.WORLD] lists the whole
 * catalog. Chinese stations are assigned to provinces by [ChinaRegions].
 */
class CountryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val code: String = savedStateHandle.get<String>("code").orEmpty().uppercase().ifEmpty { Countries.WORLD }
    val isWorld: Boolean get() = code == Countries.WORLD

    /** Set when the page shows a single region (opened from the China page). */
    val regionPage: String? = savedStateHandle.get<String>("region")?.takeIf { it.isNotBlank() }

    private val dao = AMARadioDatabase.getDatabase(application).stationDao()
    private val _uiState = MutableStateFlow(CountryUiState(selectedRegion = regionPage))
    val uiState: StateFlow<CountryUiState> = _uiState.asStateFlow()

    /** Stations per region key (China only), loaded once. */
    private var stationsByRegion: Map<String, List<DataRadioStation>> = emptyMap()
    private var allStations: List<DataRadioStation> = emptyList()

    init {
        viewModelScope.launch { load() }
    }

    fun selectRegion(id: String?) {
        if (id == _uiState.value.selectedRegion) return
        _uiState.update { it.copy(selectedRegion = id, stations = stationsFor(id)) }
    }

    private suspend fun load() {
        val entities = withContext(Dispatchers.IO) {
            runCatching { if (isWorld) dao.getTopStations(MAX_STATIONS) else dao.getTopStationsInCountry(code, MAX_STATIONS) }
                .getOrDefault(emptyList())
        }
        // The catalog lists many stations several times (one entry per stream or quality);
        // keep the most played entry of each name.
        allStations = withContext(Dispatchers.Default) {
            entities.map { it.toDataStation() }.distinctBy { it.Name.trim().lowercase() }
        }
        val regions = if (code == "CN") withContext(Dispatchers.Default) { assignChinaRegions() } else emptyList()
        _uiState.update {
            it.copy(
                isLoading = false,
                regions = regions,
                stations = stationsFor(it.selectedRegion),
                isTruncated = entities.size >= MAX_STATIONS
            )
        }
    }

    private fun stationsFor(region: String?): List<DataRadioStation> =
        if (region == null) allStations else stationsByRegion[region].orEmpty()

    private fun assignChinaRegions(): List<RegionFilter> = runCatching {
        val app = getApplication<Application>()
        val table = ChinaRegions.parse(app.assets.open(ChinaRegions.ASSET_PATH).bufferedReader().use { it.readText() })
        val assigned = allStations.mapNotNull { station -> table.classify(station.Name, station.State)?.let { it.key to station } }
        stationsByRegion = assigned.groupBy({ it.first }, { it.second })
        table.filters(stationsByRegion.mapValues { it.value.size })
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_STATIONS = 3000
    }
}
