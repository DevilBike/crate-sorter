# Crate Sorter: notes for Claude

Sorts a Spotify library into new playlists by mood and genre. The owner is Bike (DevilBike). They use it on a OnePlus phone running Android 16, through the APK.

## Hard rules from the owner
- Never change existing playlists, including their names or songs. The app only creates new private playlists. The one exception is the opt-in "Clear Liked Songs" step. It runs only after the user types `clear`, backs up every liked song to a private playlist first, checks the backup, and offers Restore.
- The user listens to music while the app runs, so the app must never use audio tricks to stay alive in the background.
- Each song goes into exactly one new playlist. The user's own choices (hand sorting, their own playlists) always beat the automatic sort.
- Every APK carries a visible version number: `1.<GitHub Actions run number>`.
- Ask before re-reading the library. Spotify rate limits are tight, so keep the number of requests low.

## Layout
- `index.html` is the entire web app in one file: React 18, Babel in the browser, the Tailwind CDN, and inline SVG icons. GitHub Pages serves it at https://devilbike.github.io/crate-sorter/. Pushing to `main` deploys it within about a minute.
- `android/` is a thin WebView wrapper that loads the Pages URL. Web-only changes reach the app without a reinstall. Only changes under `android/**` start a new APK build (`.github/workflows/android.yml`), which publishes a release with `releases/latest/download/CrateSorter.apk`.
  - `MainActivity.kt`: `KeepAliveWebView` keeps the page "visible" in the background. The `CrateNative` JS bridge exposes `sleep`, `setWorking`, `mbStart`, `mbPoll`, `lfmKey`, `appVersion`, `copy`, `batteryUnrestricted` and `askBattery`.
  - `WorkService.kt`: a dataSync foreground service with a wake lock. Its single worker thread runs the lookup queue: MusicBrainz items use the Spotify artist id, Last.fm items use `lfa:<artistId>` or `lft:<trackId>`.
  - `GenreStore.kt` holds the queue and results in SharedPreferences. `MusicBrainz.kt` and `LastFm.kt` are the HTTP clients.
  - The signing key is generated in CI and kept in actions/cache. Never commit a keystore or password.
- `tests/` holds Playwright checks against `index.html`, with Spotify, MusicBrainz and Last.fm mocked.
- A demo copy is published as the Claude artifact https://claude.ai/artifact/5QBRUH8GsLAfm9TeydT5br. The artifact sandbox blocks Spotify, so only the sample library works there.

## Map of index.html (search for these names)
- `APP_VERSION`: bump the date suffix on every web change, e.g. `'2026-10-31.4'`.
- `BASE_BUCKETS` / `BUCKETS` / `applyCustom`: the built-in playlists, each with `kw` (tag words), `title` (words in song names) and `weight`. The user's own playlists are appended and win whenever they match.
- `norm`, `MOODS`, `VAGUE`: tag matching ignores case and dashes. Mood tags weigh 0.5. Vague tags never drive suggestions.
- `classify(track, genres, lfm)` returns ranked buckets plus the reasons why. Song tags weigh 1.5, artist tags 1, and a pre-1990 release year adds 0.9.
- `buildPlan(...)`:
  - Order of steps: source filter, same-song dedupe (`songKey`), manual choices (`manualFor`: song beats artist beats auto, `skip` leaves a song out), automatic sort, combined playlists (`merges`), then optional decade split.
  - Its `where` map holds each song's bucket and reason. The hand sorter and the genre panel read it.
- `loadLibrary`: reads `/me/playlists`, Liked Songs and the user's own and collaborative playlist items, then gets MusicBrainz artist genres (the native queue in the app, JS in a browser). The result is cached in `cs.lib`.
- `runLastfm` / `lastfm` / `lastfmTodo`: Last.fm `artist.getinfo` (tags plus similar artists) and `track.gettoptags`. The native path packs similar artists into the result as `~Name`.
- `suggestArtists`: the "Suggested" tab, built from similar artists the user sorted plus tag profiles.
- `runAutomation`: creates playlists only. It is resumable through the `cs.journal` entries (`created`, `placed`). `runUndo` unfollows them.
- `runClearLiked` / `runRestoreLiked`: liked-songs clearing, 40 URIs per `DELETE`/`PUT /me/library` call.
- `makeApi`: one request at a time, at least 600 ms apart, with Retry-After and backoff. The block time is kept in `cs.blockedUntil`.
- UI components: `App` (views: welcome, loading, dashboard, sorter, running), `HandSorter`, `SourcePicker`, `GenrePanel`, `CombineForm`, `CustomEditor`, `LastfmPanel`, `ClearLikedPanel`, `UndoPanel`, `TroublePanel`.
- `report()` builds the troubleshooting log the user pastes back. Read it first when they report a problem.

## localStorage keys (`cs.*`)
- Spotify and app state: `clientId`, `tok`, `pkce`, `lib`, `mbgenres`, `journal`, `debug`, `gap`, `blockedUntil`, `background`.
- Sources and sorting choices: `skipSrc`, `srcKnown` (collaborative playlists start unticked), `off`, `hidden`, `split`, `custom`, `merges`, `mergeNames`, `manual` (`{songs, artists}`).
- Last.fm and Liked Songs: `lfm` (`{a, t}`), `lfmKey`, `unlike`.

## Spotify API facts (checked October 2026; the app runs in development mode)
- Sign-in is PKCE only; the implicit grant was removed. The redirect URI is the Pages URL with its trailing slash.
- Endpoints:
  - `/playlists/{id}/items` replaces `/tracks`, and its rows use `item`.
  - `POST /me/playlists` creates playlists.
  - Saving, removing and unfollowing go through `PUT`/`DELETE /me/library?uris=`.
- Things that are gone:
  - Artist genres and popularity.
  - Batch `/artists`.
  - audio-features.
  - Preview clips.
  - Reading items in playlists the user neither owns nor collaborates on.
- The app owner needs Premium, and at most 5 users can use the app.

## How to work
1. Edit `index.html` directly and bump `APP_VERSION`.
2. Test: `cd tests && npm install && npm test` (the Playwright Chromium is preinstalled in cloud sessions). `demo.js` covers the sample library, hand sorting, the genre panel, combining, removing and a dry run. `spotify.js` covers the mocked Spotify and Last.fm lookups, own playlists, reasons and suggestions. Both print a short report that ends in an empty error list (`[]`); the font load failures are expected offline.
3. Commit to `main` and push. Pages deploys the web app. If `android/` changed, check the "Build Android app" run and send the user `releases/latest/download/CrateSorter.apk`.
4. Optionally republish the demo artifact.
5. Tell the user whether they need a new APK. They usually don't, because the app loads the web page.
