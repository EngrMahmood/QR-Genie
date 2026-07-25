QR Genie — Release 1.0.8

Release date: 2026-07-25

Short (Play Store "What's new"):
- Fixed language switching (was stuck on English for everyone since 1.0.7).
- Added dark mode, a Settings screen, and Wi-Fi/contact QR code generation.
- Scan results now show one-tap actions (connect to Wi-Fi, call, email, add contact).
- Improved QR scan reliability in low light and at odd angles.

Long (full release notes / GitHub Release body):
This release fixes the in-app language switcher (broken since 1.0.7) and adds
several new features requested after user testing.

What's changed

Bug fixes
- Fix: Language switching was silently reverting to English for every non-English
  selection. Root cause: `android:localeConfig` in the manifest (added in 1.0.7)
  handed per-app language management to the OS, but the app also had its own
  manual `attachBaseContext` override reading a separate saved preference — the
  two fought, and the OS always won. Fixed by switching all activities to
  `AppCompatActivity` and using `AppCompatDelegate.setApplicationLocales()`
  exclusively, which is the API the OS mechanism actually expects.
- Trimmed the language picker from 24 to the 11 languages that have real
  translations (the other 13 silently fell back to English).
- Scan reliability: throttled the expensive JPEG/Bitmap ZXing fallback decode
  path (was running on nearly every unreadable frame and stalling the camera
  analyzer), pinned a consistent 1280x720 analysis resolution instead of
  leaving it to the system default, and made the scan debounce timer
  thread-safe.

New features
- Dark mode: follows the system theme, or can be set manually in Settings.
- New Settings screen: language picker (moved out of the home header), theme
  toggle, app version, privacy policy and rate-app links.
- QR code generation now supports Wi-Fi networks and contact cards (vCard), in
  addition to plain text/links.
- Smart scan results: Wi-Fi, contact, email, phone, and SMS QR codes now show a
  one-tap action (Connect to Wi-Fi, Add contact, Call, Send email, Send SMS)
  instead of just raw decoded text.
- History screen: added search and a Scanned/Generated filter, and its header
  now matches the rest of the app's style.

UI
- Home screen cards moved from full-color fills to neutral surfaces with a
  colored icon chip accent — keeps the blue/green brand identity without
  covering the whole card.

Version bump: `versionCode` incremented to 10 and `versionName` set to 1.0.8.

How to build a signed AAB (locally)
1. Place your keystore in the project root as `keystore.jks` or set the
   following environment variables: KEYSTORE_FILE, KEYSTORE_PASSWORD,
   KEY_ALIAS, KEY_PASSWORD.
2. From project root run (PowerShell):

```powershell
Set-Location "D:\QRAPP"
.\gradlew.bat :app:bundleRelease
```

Where to upload
- Upload the signed AAB from `app/build/outputs/bundle/release/` to Google
  Play Console.
- Alternatively, push this commit to `main` — `.github/workflows/build_and_publish.yml`
  builds and signs the AAB automatically using the repo's GitHub secrets, and
  will upload to the Play Console internal track if `PLAY_SERVICE_ACCOUNT_JSON`
  is configured.

Changelog entry (concise)
- 1.0.8 (2026-07-25): Fix language-switching regression; add dark mode,
  Settings screen, Wi-Fi/contact QR generation, smart scan actions, history
  search; bump versionCode to 10.
