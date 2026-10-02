package com.ounben.amaradio.fork.curated

import androidx.annotation.StringRes
import com.ounben.amaradio.R

/**
 * A curated playlist, shown on the China page of the World tab.
 *
 * Bundled playlists ship inside the APK (and can be refreshed from [updateUrl] without an
 * app release). Remote playlists are third-party lists without a redistribution licence,
 * so they are never copied into this repository: the app downloads them from their
 * original location on demand and credits the source via [homepageUrl].
 */
data class CuratedSource(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val assetPath: String? = null,
    val updateUrl: String? = null,
    val homepageUrl: String? = null,
    /** Country of the stations (radio-browser style ISO code); entries may override it with `x-country`. */
    val countryCode: String = "CN"
) {
    val isBundled: Boolean get() = assetPath != null
}

object CuratedSources {

    val beijingNational = CuratedSource(
        id = "beijing-national",
        titleRes = R.string.fork_curated_beijing_title,
        descriptionRes = R.string.fork_curated_beijing_desc,
        assetPath = "curated/beijing-national.m3u",
        updateUrl = "https://raw.githubusercontent.com/bowen-zhang01/AMARadio/master/app/src/main/assets/curated/beijing-national.m3u",
        homepageUrl = "https://github.com/bowen-zhang01/AMARadio"
    )

    val chineseUnderground = CuratedSource(
        id = "chinese-underground",
        titleRes = R.string.fork_curated_underground_title,
        descriptionRes = R.string.fork_curated_underground_desc,
        assetPath = "curated/chinese-underground.m3u",
        updateUrl = "https://raw.githubusercontent.com/bowen-zhang01/AMARadio/master/app/src/main/assets/curated/chinese-underground.m3u",
        homepageUrl = "https://github.com/bowen-zhang01/AMARadio",
        countryCode = ""
    )

    val spookyStories = CuratedSource(
        id = "spooky-stories",
        titleRes = R.string.fork_curated_spooky_title,
        descriptionRes = R.string.fork_curated_spooky_desc,
        assetPath = "curated/spooky-stories.m3u",
        updateUrl = "https://raw.githubusercontent.com/bowen-zhang01/AMARadio/master/app/src/main/assets/curated/spooky-stories.m3u",
        homepageUrl = "https://github.com/bowen-zhang01/AMARadio",
        countryCode = ""
    )

    val cnrProvincial = CuratedSource(
        id = "cnr-provincial",
        titleRes = R.string.fork_curated_provincial_title,
        descriptionRes = R.string.fork_curated_provincial_desc,
        updateUrl = "https://raw.githubusercontent.com/huangsuming/iptv/main/list/radio.m3u8",
        homepageUrl = "https://github.com/huangsuming/iptv"
    )

    val chinaChecked = CuratedSource(
        id = "china-checked",
        titleRes = R.string.fork_curated_china_title,
        descriptionRes = R.string.fork_curated_china_desc,
        updateUrl = "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/%2Bchecked%2B/c/china.m3u",
        homepageUrl = "https://github.com/junguler/m3u-radio-music-playlists"
    )

    val all: List<CuratedSource> = listOf(beijingNational, chineseUnderground, spookyStories, cnrProvincial, chinaChecked)

    fun byId(id: String): CuratedSource? = all.firstOrNull { it.id == id }
}
