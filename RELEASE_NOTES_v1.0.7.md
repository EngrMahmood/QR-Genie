QR Genie — Release 1.0.7

Release date: 2026-05-19

Short (Play Store "What's new"):
- Fixed camera black screen on first permission grant — no restart needed.
- Added language support: Arabic, Bengali, German, Spanish, Farsi, French, Hindi, Turkish, Urdu and Chinese.

Long (full release notes / GitHub Release body):
This release prepares QR Genie for Play Store update (versionCode=9 / versionName=1.0.7).

What's changed
- Fix: Camera permission handling in `ScanActivity` — the camera UI is now created only after camera permission has been granted. This prevents a black preview surface that previously required the app to be restarted after granting permission.
- Version bump: `versionCode` incremented to 9 and `versionName` set to 1.0.7.
- Native assets: Diagnostic/vendor native libraries and helper assets were prepared and included so CameraX/ML Kit native dependencies can be loaded reliably at runtime. The build process extracts problematic `.so` files into `app/src/main/assets/native/` during build.
- Translations: Added/updated several locale string resources.
- Minor UI and internal improvements (deprecated icon warnings resolved where possible, minor refactors).

Fix details and rationale
- Root cause: The camera preview (CameraX) was being initialised in `onCreate` before the runtime camera permission was granted. If the user granted the permission via the runtime dialog, the preview surface had already been initialised in a state that prevented the camera from binding correctly, producing a black view until app restart.
- Resolution: `ScanActivity` now checks `ContextCompat.checkSelfPermission` and only calls the Compose `setContent` (which initialises CameraX/Preview) after permission is present. When permission is requested and subsequently granted, the activity sets the content in the permission callback so CameraX initializes correctly immediately.

Developer / Release notes
- Build: A release AAB was generated successfully during verification. If you want the AAB attached to a GitHub Release, build a signed AAB locally (see below) and attach it to the release draft.
- Signing: Ensure release keystore is provided (via `KEYSTORE_FILE` or `keystore.jks` in project root or environment variables) before uploading the AAB to Google Play, otherwise the bundle will be unsigned.

How to build a signed AAB (locally)
1. Place your keystore in the project root as `keystore.jks` or set the following environment variables:
   - KEYSTORE_FILE (path to keystore)
   - KEYSTORE_PASSWORD
   - KEY_ALIAS
   - KEY_PASSWORD
2. From project root run (PowerShell):

```powershell
Set-Location "D:\QRAPP"
.\gradlew.bat :app:bundleRelease
```

Where to upload
- Upload the signed AAB from: `app/build/outputs/bundle/release/` to Google Play Console (Production or your preferred track).

Notes / Known issues
- The repository now contains several large vendor/diagnostic binaries (native `.so` files and zipped diagnostics). These increase repo size. Consider moving them to Git LFS or external storage and updating the build to fetch them when needed.
- If a user permanently denies camera permission ("Don't ask again"), currently the activity will close with a toast. Consider adding an in-app prompt directing users to App Settings.

Suggested Play Console "What's new" (short):
- Camera permission fix — no restart needed after granting.

Suggested GitHub release title and body
- Title: v1.0.7 — Camera permission fix
- Body: Use the Long release notes above (copy/paste).

Changelog entry (concise)
- 1.0.7 (2026-05-19): Fix camera black preview after permission grant; bump versionCode to 9; add native asset handling and locale updates.

If you'd like I can:
- Create a GitHub Release draft and attach the signed AAB (needs keystore to build signed AAB), or
- Commit this release notes file and push + create a `v1.0.7` tag and push it to origin.

