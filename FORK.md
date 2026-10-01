# Xiangyin 乡音 — a fork of AMARadio

This repository is a personal fork of [AMARadio](https://github.com/ounben/AMARadio) by ounben,
licensed under the GNU GPL v3 like the original. It is a **modified version**: changes started on
1 October 2026 and are listed below and in the git history.

The fork is published as **Xiangyin (乡音)**: it opens on a list of every country in the
radio-browser catalogue, adds curated Chinese radio playlists with designed station artwork to
the China page, replaces the interface with a Material 3 Expressive design in English and
Chinese, and fixes several playback issues, while tracking upstream releases. The worldwide
catalogue, search, favourites and upstream's filters are all still there.

## What is different from upstream

| Area | Change |
| --- | --- |
| Identity | Name **Xiangyin / 乡音**, own launcher icon and splash. Package `io.github.bowen_zhang01.amaradio`, version `<upstream>-bz<n>`, version code `upstream * 100 + n`. Installs next to upstream AMARadio. |
| World tab | The start screen: shortcuts (*Popular worldwide*, the device's country, China for a Chinese interface, recently opened countries) and every country of the catalogue grouped by continent, with localized names and station counts. A country page lists its stations, most played first. The **China** page starts with the curated playlists (Beijing carousel, CNR and CRI rows, community playlists) and can be narrowed to a province. Search also finds countries. Upstream's station tabs and filters moved behind the filter button. |
| Languages | English and Simplified Chinese throughout: country and continent names, curated station names (`x-name-en` / `x-group-en` in the playlist), station languages ("chinese" → 中文) and the remaining hard-coded upstream strings. |
| Design | Material 3 Expressive: dynamic color (wallpaper) with an amber fallback scheme, expressive motion, search app bar, short navigation bar, floating mini player, full player sheet with shape-morphing controls, segmented lists, grouped settings. The player takes its colors from the station artwork (content-based color). |
| Playback | HLS retry for HLS playlists served from URLs without `.m3u8`, correct relative URL resolution after redirects, fast failure on unrecognized formats, smoothed bandwidth readout. |
| Misc | Station icon provider authority follows the package name; CJK-aware placeholder labels; themed placeholders; obsolete upstream unit test removed. |

### Curated playlists

| Playlist | Source | Shipped in APK |
| --- | --- | --- |
| Beijing & national | `app/src/main/assets/curated/beijing-national.m3u` (this repo) | Yes. The app also refreshes it daily from `master`, so list fixes reach installed apps without a release. |
| CNR & provinces | [huangsuming/iptv](https://github.com/huangsuming/iptv) `list/radio.m3u8` | No, downloaded on demand |
| China, auto-checked | [junguler/m3u-radio-music-playlists](https://github.com/junguler/m3u-radio-music-playlists) `+checked+/c/china.m3u` | No, downloaded on demand |

The two community lists carry no licence, so they are **not** copied into this repository; the app
fetches them from their original location and credits the source.

In the bundled list, entries with `x-role="backup"` are fallback streams (they are not added to
favourites on first run), and `x-name-en` / `x-group-en` hold the English station and group names
shown in a non-Chinese interface. Every stream in it was checked by decoding audio on 2026-10-01; to
re-check, decode a few seconds of each URL with ffmpeg before committing changes.

On a fresh install with a Chinese interface the non-backup stations of the bundled list become the
initial favourites, so widgets, launcher shortcuts and Android Auto are useful immediately.

### Countries and regions

Countries, regions and station counts come from the local radio-browser database
(`StationDao` queries marked as fork additions). Country names are the platform's localized names;
continents come from a table generated from CLDR territory containment (`fork/world/Countries.kt`).
The catalogue's `Subcountry` values for China mix pinyin, postal romanization (Kiangsu, Szechuan),
Chinese and city names, so `assets/curated/cn-regions.json` maps them to the 33 province-level
divisions for the province chips. Other countries have no region chips yet: their values are too
inconsistent (Bavaria and Bayern, NSW and New South Wales) to show without a similar table.

### Branding and artwork

- **Name**: 乡音 (*Xiangyin*, "the accent of home"), after 贺知章's 少小离家老大回，乡音无改鬓毛衰.
  Strings `fork_app_name` / `fork_tagline` in `strings_fork.xml`; upstream's `app_name` is left
  alone (it is redefined in ~80 locales) and the manifest, widgets, Android Auto and TV channel
  point at `fork_app_name` instead.
- **Icon**: an amber moon rising over the sea at night (海上生明月，天涯共此时); the waves double as a
  sound wave. Adaptive icon with a monochrome layer for themed icons, status-bar icon and in-app
  logo are generated by `scripts/app-icon/generate.py` — edit the script, not the XML.
- **Splash**: `ForkSplashTheme` uses the icon's night color, and `SplashActivity` draws the same
  mark at the system splash position, so the Android 12+ splash hands off seamlessly.
- **Station artwork**: full-bleed tiles for the 22 stations of the bundled playlist (region,
  short name, frequency or full name on a gradient), generated by `scripts/station-art/generate.py`
  with Noto Sans SC (SIL OFL). Referenced as `tvg-logo="asset://curated/logos/<id>.png"`; the app
  copies them into app storage so notifications, the lock screen and Android Auto show them too.
  They are original designs, not broadcaster logos.

### Fork-specific files

Kept separate so upstream merges rarely conflict:

- `app/fork.gradle` (applied from the last line of `app/build.gradle`): package name, versioning,
  release signing, the material3 1.5 alpha dependency.
- `app/src/main/kotlin/com/ounben/amaradio/fork/` — curated playlists (`curated/`), the country
  catalogue (`world/`) and the World, country and playlist screens (`ui/`).
- `app/src/main/kotlin/com/ounben/amaradio/database/CatalogCounts.kt` — result types of the
  country queries.
- `app/src/main/res/values*/strings_fork.xml`, `colors_fork.xml`, `styles_fork.xml` — all new
  resources (strings in English and Simplified Chinese; app name also in Traditional Chinese).
- `res/drawable/ic_*xiangyin*`, `res/mipmap-anydpi-v26/ic_launcher_xiangyin*` — generated icons.
- `app/src/main/assets/curated/` (playlist, artwork and the China region table), `scripts/` (icon,
  artwork and release scripts), `.github/workflows/upstream-sync.yml`.

Rewritten upstream files (expect manual merges when upstream changes them): `ui/Theme.kt`,
`ui/MainScreen.kt`, `ui/MainTopBar.kt`, `ui/PlayerScreens.kt`, `ui/StationComposables.kt`.
Lightly edited upstream files: `ui/SettingsScreen.kt`, `ui/AboutScreen.kt`, `ui/StationTabContainer.kt`,
`ui/SettingsViewModel.kt`, `ui/PlayerViewModel.kt`, the colour-only edits in other `ui/` screens,
the string-resource edits in `ui/ServerInfoScreen.kt`, `ui/Dialogs.kt`, `ui/CategoriesScreen.kt`
and `ui/FilterViewModel.kt`, the queries appended to `database/StationDao.kt`,
`players/exoplayer/ExoPlayerWrapper.kt`, `players/exoplayer/IcyDataSource.kt`,
`utils/StationIconProvider.kt`, `utils/StationPlaceholderUtils.kt`, `AndroidManifest.xml`,
`res/values/arrays.xml` and one translation fix in `res/values-zh-rCN/strings.xml`.

The interface needs the **material3 1.5 alpha** line: the Compose BOM still pins 1.4.0, which does
not ship the Expressive components. Move back to the BOM version once 1.5 is stable.

## Building

Requirements: JDK 21, Android SDK platform 37, and **git-lfs** (the prebuilt station database
`app/src/main/assets/databases/radio_browser_database.db` is stored in Git LFS; without it the
APK ships a pointer file and offline search stays empty until the first online sync).

```sh
git lfs install --local
git lfs fetch upstream master && git lfs checkout   # LFS objects live in the upstream repo
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```

## Releasing

1. Bump `forkRevision` in `app/fork.gradle` (reset to 1 after merging a new upstream version).
2. Add a section for the new version to the changelog below.
3. Run `scripts/fork-release.sh --publish`. It verifies the station database, runs the unit tests,
   builds the signed `foss` release APK and attaches it to a GitHub release.

The signing key is **not** in the repository: `~/.android/amaradio-fork-release.jks`, password in
the macOS keychain item `amaradio-fork-release`. Keep a backup of both — Android only installs
updates signed with the same key.

## Syncing upstream

`.github/workflows/upstream-sync.yml` runs every Monday (and on demand). When ounben/AMARadio has
new commits it merges them into a `sync/upstream-<sha>` branch and opens a pull request; on merge
conflicts it opens an issue instead. Pull requests opened by the workflow do not trigger CI on
their own (GitHub blocks workflow-created events), so build locally before merging.

Manual sync:

```sh
git fetch upstream
git merge upstream/master            # resolve conflicts, keeping the fork's design
git lfs fetch upstream master && git lfs checkout
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```

When upstream changes one of the rewritten UI files, port the behaviour change into the fork's
version instead of taking upstream's layout.

## Changelog

### 1.40-bz2

- The app opens on **World**: every country of the catalogue by continent, with shortcuts to
  *Popular worldwide*, your country and recently opened countries. Pick a country to see its
  stations; China's page holds the curated Beijing, CNR and CRI playlists and province filters.
- Search also finds countries ("Japan", "日本", "jp").
- English and Chinese everywhere: English names for the curated stations, localized country,
  continent and language names, grouped numbers, and the last hard-coded upstream strings.
- The bottom bar is now World, Favourites, History, Settings; upstream's station tabs and filters
  open from the filter button on World.
- Lists mark a station as playing only while it plays, not while paused.
- A fresh install seeds the Beijing favourites only for a Chinese interface.

### 1.40-bz1

First release of the fork, based on upstream AMARadio 1.40.

- Renamed to Xiangyin (乡音) with a new icon, splash and designed artwork for every station of
  the Beijing & national playlist; the player takes its colors from the station artwork.
- New Home tab with curated Chinese playlists: Beijing & national (bundled, verified), CNR &
  provinces and China auto-checked (downloaded on demand, grouped, searchable).
- Material 3 Expressive interface with dynamic color, pure black option, new player and lists.
- Fresh installs start with the Beijing and national stations as favourites.
- HLS streams behind redirects or URLs without `.m3u8` now play (previously failed after about a
  minute of "Connecting…").
- Bandwidth readout averages over 10 seconds, so HLS stations no longer show 0.0 kB/s.
- Installs alongside upstream AMARadio.
