QR Genie — Release 1.0.9

Release date: 2026-08-12

Short (Play Store "What's new"):
- New QR types: Email, SMS, Location, and Calendar Event (alongside Text, Wi-Fi, Contact, and Bank account).
- New: favorites — star and filter your most-used history items.
- New: in-app rating prompt after repeated successful scans/generations.
- Unreadable/binary QR codes now show a clean "not readable" message instead of garbled text.
- Bank Account QR type, Filipino language, and complete translations for all 10 existing languages (carried over from the previous cycle).
- Various UI polish and bug fixes.

Long (full release notes / GitHub Release body):
This release expands QR generation to 8 total types, adds favorites and an
in-app rating prompt, and turns on AdMob advertising for the first time
(banner + interstitial), backed by a published GDPR/US-states consent flow
and an updated privacy policy.

What's changed

New features
- Four new Generate QR types: Email (with subject/body), SMS, Location
  (lat/lng), and Calendar Event (title/location/start/end) — joining the
  existing Text, Wi-Fi, Contact, and Bank account types for 8 total.
- Favorites: star any History item and filter the list to favorites only.
- In-app rating prompt (Google Play In-App Review API), shown after 3
  successful scan/generate actions, gated so it only ever asks once.
- AdMob integration is now live: banner + interstitial ads using real ad
  unit IDs (previously test IDs), gated behind a published UMP consent flow
  (GDPR message for EEA/UK/Switzerland, US states privacy message).

Bug fixes / infrastructure
- Fixed a build.gradle.kts ordering bug where the release signingConfig was
  looked up before it was created, causing `bundleRelease` to silently
  produce an unsigned .aab (Play Console rejected it with "All uploaded
  bundles must be signed"). Signing config creation now runs before
  buildTypes so the lookup succeeds.
- Enabled `isShrinkResources` alongside the existing R8 minification, per
  Play Console's performance recommendation on the previous release.
- Fixed a History screen layout bug where the new "Favorites" filter chip
  wrapped its label vertically ("Fa/vo/rit/es") because the chip row didn't
  scroll; the row is now horizontally scrollable.
- Carried over from the previous cycle: fixed garbled display of
  unreadable/binary QR codes, added Bank Account QR type, added Filipino
  language, completed translations for all 10 existing languages.

Privacy & policy
- Privacy policy (https://sites.google.com/view/qrgenieprivacypolicy/home)
  updated to disclose AdMob advertising, third-party data sharing (Device or
  other IDs, for advertising/marketing), and how users can manage ad
  consent.
- Play Console App content updated: Ads declaration ("Yes, my app contains
  ads"), Data safety (Device or other IDs — collected and shared, for
  advertising/marketing, user choice, not required, encrypted in transit).

Version bump: `versionCode` incremented to 11 and `versionName` set to 1.0.9.

How to build a signed AAB (locally)
1. Place your keystore in the project root as `keystore.jks` or set the
   following environment variables: KEYSTORE_FILE, KEYSTORE_PASSWORD,
   KEY_ALIAS, KEY_PASSWORD.
2. From project root run (PowerShell):

```powershell
Set-Location "D:\QRAPP"
.\gradlew.bat :app:bundleRelease
```

3. Verify the output is actually signed before uploading:

```powershell
jarsigner -verify "app\build\outputs\bundle\release\app-release.aab"
```
   It must print "jar verified." — if it prints "jar is unsigned.", the
   signingConfig didn't get picked up (check that the keystore path/env vars
   are correct and that build.gradle.kts creates the signing config before
   referencing it in buildTypes).

Where to upload
- Upload the signed AAB from `app/build/outputs/bundle/release/` to Google
  Play Console → Production → Create new release. A staged rollout
  (e.g. 20-50% before 100%) is recommended for this release since it turns
  on ads and adds several new features.
- Alternatively, push this commit to `main` — `.github/workflows/build_and_publish.yml`
  builds and signs the AAB automatically using the repo's GitHub secrets, and
  will upload to the Play Console internal track if `PLAY_SERVICE_ACCOUNT_JSON`
  is configured.

Changelog entry (concise)
- 1.0.9 (2026-08-12): Add Email/SMS/Location/Event QR generation, favorites
  in History, in-app rating prompt; turn on live AdMob ads with published
  GDPR/US-states consent and updated privacy policy; fix a signing-config
  ordering bug that produced unsigned release bundles; enable resource
  shrinking; bump versionCode to 11.
