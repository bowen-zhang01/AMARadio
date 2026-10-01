package com.ounben.amaradio.fork.world

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ounben.amaradio.database.AMARadioDatabase
import com.ounben.amaradio.database.toDataStation
import com.ounben.amaradio.station.DataRadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CountryUiState(
    val isLoading: Boolean = true,
    val stations: List<DataRadioStation> = emptyList(),
    val regions: List<RegionFilter> = emptyList(),
    val selectedRegion: String? = null,
    /** True when the list stops at [CountryViewModel.MAX_STATIONS]; search finds the rest. */
    val isTruncated: Boolean = false
)

/**
 * One country page (route `country/{code}`): its stations from the local catalog, most played
 * first, optionally narrowed to a region. [Countries.WORLD] lists the whole catalog.
 */
class CountryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val code: String = savedStateHandle.get<String>("code").orEmpty().uppercase().ifEmpty { Countries.WORLD }
    val isWorld: Boolean get() = code == Countries.WORLD

    private val dao = AMARadioDatabase.getDatabase(application).stationDao()
    private val _uiState = MutableStateFlow(CountryUiState())
    val uiState: StateFlow<CountryUiState> = _uiState.asStateFlow()
    private var stationsJob: Job? = null

    init {
        if (code == "CN") viewModelScope.launch {
            val regions = withContext(Dispatchers.IO) { loadChinaRegions() }
            _uiState.update { it.copy(regions = regions) }
        }
        loadStations()
    }

    fun selectRegion(id: String?) {
        if (id == _uiState.value.selectedRegion) return
        _uiState.update { it.copy(selectedRegion = id) }
        loadStations()
    }

    private fun loadStations() {
        stationsJob?.cancel()
        val state = _uiState.value
        val region = state.regions.firstOrNull { it.id == state.selectedRegion }
        _uiState.update { it.copy(isLoading = true) }
        stationsJob = viewModelScope.launch {
            val entities = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        isWorld -> dao.getTopStations(MAX_STATIONS)
                        region != null -> dao.getTopStationsInRegions(code, region.catalogNames, MAX_STATIONS)
                        else -> dao.getTopStationsInCountry(code, MAX_STATIONS)
                    }
                }.getOrDefault(emptyList())
            }
            // The catalog lists many stations several times (one entry per stream quality);
            // keep the most played entry of each name.
            val stations = withContext(Dispatchers.Default) {
                entities.map { it.toDataStation() }.distinctBy { it.Name.trim().lowercase() }
            }
            _uiState.update {
                it.copy(isLoading = false, stations = stations, isTruncated = entities.size >= MAX_STATIONS)
            }
        }
    }

    private suspend fun loadChinaRegions(): List<RegionFilter> = runCatching {
        val app = getApplication<Application>()
        val table = ChinaRegions.parse(app.assets.open(ChinaRegions.ASSET_PATH).bufferedReader().use { it.readText() })
        table.filters(dao.getRegionCounts(code))
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_STATIONS = 2000
    }
}
