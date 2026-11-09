# MyLinks

> [!NOTE]
> **99% vibecoded, still works.** GPL-3.0-or-later.

An offline-first Android companion for a self-hosted [Shiori](https://github.com/go-shiori/shiori)
(1.8.x) library. Your links live on your phone; the server is where they are backed up and
shared with the web UI. No Google services, no analytics, no account beyond your own instance.

## What it does

- **Library that works on a plane.** Every bookmark is mirrored into local SQLite on first sync.
  Search, tags and date filters all run against the device, never the network.
- **Share target.** Share any link from a browser, feed reader or chat app; MyLinks opens a save
  sheet with the URL filled in. The bookmark is stored immediately and uploaded later.
- **Quick tags.** Type a tag or tap one of your frequently used ones while saving. Add a tag to an
  existing bookmark from its detail screen.
- **Quick filter.** Tap tag chips under the search field; the list narrows instantly. Chips
  combine as AND, and the selected ones float to the front.
- **Read offline.** Shiori's extracted article text is stored on the phone (a few articles per
  sync, or on demand), so `Read` opens the full text with the radio off — plain text, no web view.
- **All your tags.** A dedicated tag screen — alphabetical, with counts, searchable and
  multi-select — instead of a strip that scrolls off the screen.
- **Search by date.** Presets (today, 7 days, 30 days, this year) plus an arbitrary range from
  Material's range picker. The list is grouped under day headers.
- **Delayed sync.** Sync on launch, on resume, on pull-to-refresh, right after a save, and every
  6 hours in the background. Everything queued while offline is replayed in order.

## Screenshots

Captured on an AOSP Android 16 device image (no Google APIs) against a live Shiori 1.8.0.

| Library | Tag filters | Reading offline |
|---|---|---|
| <img src="docs/screenshots/library.png" width="240" alt="Library with day groups"> | <img src="docs/screenshots/tags.png" width="240" alt="Tag screen with counts and multi-select"> | <img src="docs/screenshots/reader.png" width="240" alt="Reader showing cached article text in airplane mode"> |
| Search, day groups, and a `Tags` entry instead of a scrolling strip. | Every tag, alphabetical with counts; selection is the filter. | Cached article text, read with the radio off. |

| Library (dark) | Bookmark | Save a link |
|---|---|---|
| <img src="docs/screenshots/library-dark.png" width="240" alt="Library in dark mode"> | <img src="docs/screenshots/detail.png" width="240" alt="Bookmark detail with Read as the primary action"> | <img src="docs/screenshots/save-link.png" width="240" alt="Save-a-link sheet"> |
| Same palette at night. | Read first, then browser, share, delete. | Opened from the share sheet with the URL already in place. |

| Date filter | Nothing matches | Offline queue |
|---|---|---|
| <img src="docs/screenshots/date-filter.png" width="240" alt="Date filter sheet"> | <img src="docs/screenshots/empty-state.png" width="240" alt="Empty filter state"> | <img src="docs/screenshots/offline-queue.png" width="240" alt="Offline queue with a pending bookmark"> |
| Presets plus an arbitrary range. | Tag filters are AND-ed, so this is the honest empty state. | A link saved in airplane mode: accent dot, "1 queued". |

| Sign in |  |  |
|---|---|---|
| <img src="docs/screenshots/sign-in.png" width="240" alt="Sign in screen with URL validation and recent servers"> |  |  |
| Exact URL required, recent servers suggested, credentials offered to password managers. |  |  |

## Use it

Requirements: Android 8.0 (API 26) or newer, and a reachable Shiori 1.8.x instance.

```bash
./build.sh :app:assembleDebug        # debug APK (debug key, installs as-is)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Sign in with the exact address of your instance, scheme included (`http://box:8080` or
`https://shiori.example.com`) — the same URL you would open in a browser. Nothing is guessed: a
missing scheme is reported inline instead of silently retried over the other protocol. Once you
have signed in, the address and username are remembered, and recently used servers are offered as
suggestions while typing.

`./build.sh` is a convenience wrapper around a toolchain staged in `.build-env/` (useful in
sandboxes where `$HOME` is read-only). Normally just use `./gradlew` with JDK 17+ and
`ANDROID_HOME` pointing at an SDK with platform 36.

### Building a signed release APK

`app-release.apk` is ~1.6 MB (R8 + resource shrinking). Signing is opt-in: create a key once, then
release builds are signed with it; without a key the release APK is simply left unsigned.

```bash
keytool -genkeypair -keystore release.keystore -alias mylinks \
        -keyalg RSA -keysize 4096 -validity 10000
cat > keystore.properties <<EOF
storeFile=release.keystore
storePassword=your-password
keyAlias=mylinks
keyPassword=your-password
EOF

./build.sh :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
.build-env/android-sdk/build-tools/36.0.0/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`, `*.keystore` and `dist/` are gitignored — the signing key never enters the
repository. Keep it: Android only replaces an installed app in place when the signature matches.

### Releasing

Pushing a tag that starts with `v` runs `.github/workflows/release.yml`, which tests, builds a
signed APK and publishes it as a GitHub release (`mylinks-<version>.apk` + `SHA256SUMS.txt`).
`versionName` comes from the tag, `versionCode` from the commit count, so nothing needs editing
before a release.

One-time setup:

1. Create the signing key (prints everything you need, including the base64 blob):

   ```bash
   ./scripts/make-keystore.sh
   ```

2. Add the four secrets at *Settings → Secrets and variables → Actions*:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

3. Release:

   ```bash
   git tag -a v1.1.0 -m "MyLinks 1.1.0"
   git push origin v1.1.0
   ```

Without `KEYSTORE_BASE64` the workflow fails on purpose rather than publishing an APK nobody can
upgrade over. Pull requests and pushes to `main` run only the checks (`.github/workflows/ci.yml`),
no secrets needed.

## How offline and sync work

Two tables and a queue:

- `bookmarks` + `bookmark_tags` mirror the server. `server_id` is null for rows created on the
  phone and not yet uploaded; those show a small queued dot in the list.
- `outbox` holds pending mutations (`create`, `tag`, `delete`), replayed oldest-first on the next
  successful sync.

A sync is **push, then pull**:

1. Every outbox entry is replayed. A 4xx response drops the entry (the server will never accept
   it, so retrying forever would be wrong); network errors leave the queue intact for later.
2. The pull walks `/api/bookmarks?page=N`. A bookmark is "already mirrored" only when its
   modified time, title, url and tag set all match, so a server-side tag edit or a metadata
   refresh that lands in the same second still converges. Incremental mode stops at the first page
   where every bookmark matches, which is normally 1–2 requests.
3. Every 12 hours (and on every manual refresh) the pull is *full*: all pages are read and any
   mirrored bookmark the server no longer has is deleted locally. This is also how edits made in
   the web UI reach the phone.

Pull is last-writer-wins by `modifiedAt`. Local edits are never overwritten mid-queue because the
push happens first.

Article text is fetched from `GET /api/v1/bookmarks/{id}/readable`, which returns the readable
copy Shiori already extracted when it crawled the page. Each sync downloads up to eight missing
articles, newest first; the toggle in settings turns that off. A row that the server has no text
for is marked checked anyway, so it is not retried on every sync.

Sessions are Shiori's JWT with `rememberMe`, so they last 30 days; after that the sign-in screen
comes back with your server and username pre-filled. Shiori 1.8 has no bookmark endpoints on its
v1 API yet, so MyLinks talks to the legacy endpoints for bookmark CRUD.

## Design notes

The look is editorial rather than stock Material: warm paper, ink, a single oxblood accent,
hairlines instead of cards, an oversized serif for titles (the platform serif, so nothing is
downloaded) and monospace for metadata and dates. The list has no thumbnails on purpose — cover
images would need a network round trip and a disk cache that is useless offline, and the
type-only list is faster to scan.

## Constraints kept

- **No Google runtime.** No play-services, Firebase, Play Billing, Play Asset Delivery,
  downloadable fonts or Google-hosted runtime endpoint. WorkManager without play-services falls
  back to the platform JobScheduler, so background sync works on de-Googled ROMs. `google()` is
  still in `settings.gradle.kts` because that is where AndroidX libraries are published — that is
  a build-time fetch, not a runtime dependency.
- **Light.** No image loader, no DI framework, no navigation library, no JSON library, no
  Retrofit: `HttpURLConnection` + `org.json` (both platform) plus Compose and WorkManager. Icons
  are 12 small vector drawables exported from Material Design Light / Phosphor. Release builds are
  R8-minified and resource-shrunk.

## Verified on a device

The whole flow was exercised on an **AOSP Android 16 x86_64 emulator (no Google APIs at all)**
against a live Shiori 1.8.0: sign in, sync 9 bookmarks, day groups, search, tag filtering, date
filtering, empty state, detail, share target, offline save, reconnect, quick tag add and delete —
each step checked against both the app's SQLite file and the server's API.

That run found six real defects, all fixed:

- `PRAGMA busy_timeout` was executed through `execSQL`, which throws on Android: **crash on first
  open**.
- Shiori rewrites a bookmark right after fetching its metadata, and its timestamps have
  one-second resolution, so a same-second edit looked "already synced" and the fetched title
  never arrived. Freshness is now a content comparison (see below).
- The outbox queued tag additions **without the tag name**, so every sync sent an empty tag batch
  and the server panicked — an endless retry loop.
- Tag edits made on the server do not always bump `modifiedAt`, so the mirror could keep tags the
  server had dropped. `needsUpdate` now compares title, url and the tag set as well.
- The `+ tag` field on the detail screen opened without focus, so typing went nowhere.
- Both sheets clipped their footer behind the navigation bar.

## Verified against a real server

The client was developed against a live Shiori 1.8.0 build, not just its documentation, and two
things only the real server revealed are handled in `Api.kt`:

- **Responses are enveloped.** Shiori wraps JSON as `{"ok":true,"message":{...}}`. MyLinks sends
  `X-Shiori-Response-Format: new` (the server's documented opt-out) *and* unwraps defensively, so
  it works whether or not the envelope is present.
- **The login field is `expires`**, although the swagger annotation says `expiration`. Both are
  read.

`scripts/e2e.sh` boots a throwaway Shiori on 127.0.0.1:18080 (building it from the v1.8.0 tag if
needed) and runs the contract tests: sign in, create a bookmark, add a tag, find it on page 1 with
parsed UTC timestamps, delete it, and confirm a wrong password and a scheme-less LAN address both
behave.

## Layout

```
app/src/main/java/net/reddec/mylinks/
  data/Api.kt      Shiori HTTP client (login, page, create, addTags, delete)
  data/Store.kt    all SQLite reads/writes, outbox
  data/Sync.kt     push-then-pull reconciliation
  data/Time.kt     server timestamp parsing, relative dates, day headers
  data/Links.kt    share-text URL extraction, server address candidates
  work/           WorkManager 6-hourly sync
  ui/             theme, library, save sheet, detail, settings, sign-in
  MyLinksViewModel.kt, MainActivity.kt
```

## Endpoints used

| Purpose | Endpoint |
|---|---|
| Sign in | `POST /api/v1/auth/login` → JWT |
| List (paged, 30/page) | `GET /api/bookmarks?page=N` |
| Create | `POST /api/bookmarks` |
| Add tags | `PUT /api/bookmarks/tags` |
| Delete | `DELETE /api/bookmarks` |
| Article text | `GET /api/v1/bookmarks/{id}/readable` |

All requests send `Authorization: Bearer <token>`.

## Tests

```bash
./build.sh :app:testDebugUnitTest    # 11 hermetic unit tests
./scripts/e2e.sh                     # + 3 contract tests against a live Shiori 1.8.0
```

The emulator run is reproducible too: `avdmanager create avd -n mylinks -k
"system-images;android-36;default;x86_64"` boots an image with no Google services, which is also
how the "no GApps" claim was tested rather than assumed.

Unit tests cover timestamp parsing (SQLite UTC strings, ISO with zone), relative/grouped date
rendering, share-text URL extraction and server-address candidates. The e2e tests are skipped
unless `MYLINKS_E2E_SERVER` is set, so the normal suite never touches the network.

## Known limits

- Tag removal from a bookmark, renames and bulk edits are not implemented (Shiori needs a full
  `PUT /api/bookmarks` for that); tag *additions* are.
- The reader shows plain text: no images, no link targets, no per-article typography settings.
- Article text costs storage and sync time, so it is capped at eight new articles per sync; older
  ones fill in over successive syncs, or immediately when you press the refresh icon in the reader.
- Search is local only, so it cannot match archived page bodies that were never mirrored.
- One server, one account per install.
- The JWT is stored in the app's private `SharedPreferences`, not encrypted with the Keystore.
- Cleartext HTTP is allowed app-wide so LAN instances work; use https for anything exposed.
- Verified on an AOSP emulator, not on physical hardware: real-device fonts metrics, gesture
  navigation and OEM quirks are still unseen.
- Incremental sync only sees *new* bookmarks quickly; edits made in the web UI arrive with the
  next full pull (at most 12 hours, or immediately on pull-to-refresh).

## License

GPL-3.0-or-later — see [LICENSE](LICENSE). MyLinks is an independent client: it talks to Shiori's
HTTP API and contains none of Shiori's code, so Shiori's own MIT license is unaffected.
