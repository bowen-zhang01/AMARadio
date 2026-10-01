# AMARadio — bowen-zhang01 fork

This repository is a personal fork of [AMARadio](https://github.com/ounben/AMARadio) by ounben,
licensed under the GNU GPL v3 like the original. It is a **modified version**: changes started on
1 October 2026 and are listed below and in the git history.

The fork adds curated Chinese radio playlists, replaces the interface with a Material 3
Expressive design and fixes several playback issues, while tracking upstream releases.

## What is different from upstream

| Area | Change |
| --- | --- |
| Identity | Package `io.github.bowen_zhang01.amaradio`, version `<upstream>-bz<n>`, version code `upstream * 100 + n`. Installs next to upstream AMARadio. |
| Home tab | New first tab with the bundled *Beijing & national* playlist (BRTV, CNR and CRI official streams) plus two community playlists. |
| Design | Material 3 Expressive: dynamic color (wallpaper) with an amber fallback scheme, expressive motion, search app bar, short navigation bar, floating mini player, full player sheet with shape-morphing controls, segmented lists, grouped settings. |
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
favourites on first run). Every stream in it was checked by decoding audio on 2026-10-01; to
re-check, decode a few seconds of each URL with ffmpeg before committing changes.

On a fresh install the non-backup stations of the bundled list become the initial favourites, so
widgets, launcher shortcuts and Android Auto are useful immediately.

### Fork-specific files

Kept separate so upstream merges rarely conflict:

- `app/fork.gradle` (applied from the last line of `app/build.gradle`): package name, versioning,
  release signing, the material3 1.5 alpha dependency.
- `app/src/main/kotlin/com/ounben/amaradio/fork/` — curated playlists and the Home screens.
- `app/src/main/res/values*/strings_fork.xml` — all new strings (English and Simplified Chinese).
- `app/src/main/assets/curated/`, `scripts/fork-release.sh`, `.github/workflows/upstream-sync.yml`.

Rewritten upstream files (expect manual merges when upstream changes them): `ui/Theme.kt`,
`ui/MainScreen.kt`, `ui/MainTopBar.kt`, `ui/PlayerScreens.kt`, `ui/StationComposables.kt`.
Lightly edited upstream files: `ui/SettingsScreen.kt`, `ui/AboutScreen.kt`, `ui/StationTabContainer.kt`,
`ui/SettingsViewModel.kt`, `ui/PlayerViewModel.kt`, the colour-only edits in other `ui/` screens,
`players/exoplayer/ExoPlayerWrapper.kt`, `players/exoplayer/IcyDataSource.kt`,
`utils/StationIconProvider.kt`, `utils/StationPlaceholderUtils.kt`, `AndroidManifest.xml`,
`res/values/arrays.xml`.

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

### 1.40-bz1

First release of the fork, based on upstream AMARadio 1.40.

- New Home tab with curated Chinese playlists: Beijing & national (bundled, verified), CNR &
  provinces and China auto-checked (downloaded on demand, grouped, searchable).
- Material 3 Expressive interface with dynamic color, pure black option, new player and lists.
- Fresh installs start with the Beijing and national stations as favourites.
- HLS streams behind redirects or URLs without `.m3u8` now play (previously failed after about a
  minute of "Connecting…").
- Bandwidth readout averages over 10 seconds, so HLS stations no longer show 0.0 kB/s.
- Installs alongside upstream AMARadio.
