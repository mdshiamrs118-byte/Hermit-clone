# Nutshell

![Android CI](../../actions/workflows/android.yml/badge.svg)

An original, open source "lite apps" browser for Android. Turn any website into its own
app-like window with its own color, settings, and isolated storage.

This is an independent project. It does not contain code, assets, branding, or server endpoints
from any other app.

## Features

| Feature | Where |
|---|---|
| Create button speed-dial: Create Your Own / Ready-made Lite Apps | `MainActivity.kt` |
| Ready-made gallery: 397 sites in 53 categories, search, jump between categories, tap to add | `GalleryActivity.kt`, `Catalog.kt` |
| Home screen: list and grid views with site icons, color dots, search / URL box | `MainActivity.kt`, `IconStore.kt` |
| Lite App Settings screen and every sub-page (Sandbox, Behavior, Privacy, Theme, Tags, Bookmarks, Feeds, Web Monitors, Search, Share) | `SettingsActivity.kt`, `UiKit.kt` |
| Sandboxes (22 named storage containers, clear cache / storage) | `Sandboxes.kt` |
| Content Blocker lists page (enable, add, remove, refresh) | `ContentBlocker.kt`, `SettingsActivity.blockerPage` |
| Theme: light / dark / system, custom icon (pick, draw, letters), color | `SettingsActivity.themePage`, `IconStore.kt` |
| Lite apps (name, URL, color, per-app settings) | `Model.kt`, `AppEditor.kt` |
| Separate Recents entry per lite app | `documentLaunchMode="always"` in the manifest, `BrowserActivity.applyTaskDescription` |
| Home-screen shortcuts | `Util.kt` `pinShortcut` |
| Isolated cookies/storage per app (WebView profiles) | `BrowserActivity.attachProfile` |
| Incognito tabs (temporary profile, deleted on close) | `BrowserActivity` |
| Ad/tracker blocking, updatable hosts-file lists | `ContentBlocker.kt`, `shouldInterceptRequest` |
| User scripts (`@match`/`@include`, `GM_addStyle`) | `UserScripts.kt` |
| Keep off-site links outside the app, login providers stay in | `shouldOverrideUrlLoading`, `isAuthHost` |
| Camera / mic / location prompts (per-app switch + Android prompt) | `WebChromeClient` in `BrowserActivity` |
| File upload, fullscreen video, JS dialogs | `WebChromeClient` |
| Downloads, including `blob:` URLs | `download()`, `fetchBlob()`, `BlobBridge` |
| Quick Settings side panel (three-dot button) | `QuickPanel.kt`, host callbacks in `BrowserActivity` |
| Reader mode, view source, print/PDF, find in page | panel tiles |
| Content blocker, block popups, desktop mode, frameless, full-screen, dark mode toggles | panel toggle tiles |
| Text size slider, back / forward / reload / home row | panel |
| Per-app user scripts: built-ins (no autoplay, force text copy, force zoom, Google Translate, snow, remove footers) plus your own | `UserScripts.kt`, `GlobalSettings.kt` |
| Feed and web-monitor notifications (hourly) | `FeedWorker.kt` |
| Share target ("Open in…") | `ShareActivity.kt` |

## Build on GitHub

1. Create a new repository and push this project to it (the `main` branch).
2. Open the **Actions** tab. The **Android CI** workflow runs on every push and pull request,
   and you can also start it by hand with **Run workflow**.
3. When it finishes, open the run and download **nutshell-debug-apk** from *Artifacts*.
   Unzip it and install the `.apk` on your phone (allow installs from unknown sources).
4. To publish a download on the Releases page, push a tag: `git tag v1.0.0 && git push origin v1.0.0`.

The workflow builds a **debug** APK, which is signed with the standard debug key. That is fine for
personal use and testing, but not for the Play Store. A signed release build needs your own keystore
stored in GitHub Secrets; ask if you want that added.

## Build locally

1. Open the folder in **Android Studio** (Ladybug or newer, JDK 17).
2. Let Gradle sync. The project has `gradle-wrapper.properties` but no wrapper jar/scripts;
   Android Studio downloads Gradle 8.9 itself, or run `gradle wrapper` once if you want `./gradlew`.
3. Run on a device or emulator with API 29+.

Isolated profiles need a recent Android System WebView (Chrome 132+). On older WebViews the app
still works; lite apps then share one cookie store and incognito data is wiped when the tab closes.

## Notes and known limits

* Written without access to an Android SDK, so it has **not been compiled or run**. Expect to fix a
  few small compile errors on first sync.
* Cleartext (`http://`) is off. Set `android:usesCleartextTraffic="true"` if you need it.
* UI strings are inline English for brevity; move them to `strings.xml` to localize.
* The `blob:` download bridge is exposed to every frame. It only accepts data that matches a
  one-time token the app just issued, but a stricter version would use `WebViewCompat.addWebMessageListener`
  with an origin allow-list.
* "Same site" detection is a simple last-two-labels comparison, not the Public Suffix List.
* Scripts run when a page finishes loading (not at document start), so *Disable Video AutoPlay* can
  miss a video that starts playing immediately.
* Web Monitors read the page's HTML (with jsoup), so content that a page builds with JavaScript can't be watched.
* Sandbox sizes are read from WebView's folders on disk and may show 0 on WebView versions that lay them out differently.
* The gallery's web addresses were written from memory. A handful of obscure entries (for example App Sales, Volta, iCal Studio, Inbox Attack, Guitar Tuner) may point to the wrong place; fix any of them in Lite App Settings → URL.
* The "FMHY" gallery sections list only legitimate sites recommended by the FMHY community directory. Streaming, torrent and cracked-software sites it also lists are deliberately left out.
* Gallery icons are letter badges until you turn on "Show site icons", which downloads each visible site's favicon.
* Not included: home-screen widgets, quick-settings tile, HTTP auth prompts, a bundled content-filter
  rule format beyond hosts lists, translations.
* The default block list is the public StevenBlack hosts list. Change it under *Block lists…*.
