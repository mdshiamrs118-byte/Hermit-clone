# Nutshell

![Android CI](../../actions/workflows/android.yml/badge.svg)

An original, open source "lite apps" browser for Android. Turn any website into its own
app-like window with its own color, settings, and isolated storage.

This is an independent project. It does not contain code, assets, branding, or server endpoints
from any other app.

## Features

| Feature | Where |
|---|---|
| Lite apps (name, URL, color, per-app settings) | `Model.kt`, `AppEditor.kt`, `MainActivity.kt` |
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
| Reader mode, view source, print/PDF, find in page | menu in `BrowserActivity` |
| Desktop site, text size, forced dark mode | per-app settings |
| Feed notifications (RSS/Atom, hourly) | `FeedWorker.kt` |
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
* Not included: home-screen widgets, quick-settings tile, HTTP auth prompts, a bundled content-filter
  rule format beyond hosts lists, translations.
* The default block list is the public StevenBlack hosts list. Change it under *Block lists…*.
