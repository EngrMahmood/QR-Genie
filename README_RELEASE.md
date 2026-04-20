Release checklist and build instructions
=====================================

This file describes how to prepare and publish a release of QR Genie (com.qrgenie.app).

1) Version bump
   - Update `app/build.gradle.kts`:
     - Increment `versionCode` (e.g. 5 -> 6)
     - Update `versionName` (e.g. "1.0.3" -> "1.0.4")

2) Signing
   - Create or locate your release keystore (e.g., `qrgenie-release.jks`).
   - Configure signing in `app/build.gradle.kts` or use `gradle.properties` with environment variables.

3) Build the signed AAB locally
   - On Windows PowerShell (from repository root):

```powershell
.\gradlew.bat clean bundleRelease
```

   - The AAB will be in `app/build/outputs/bundle/release/`.

4) Test on internal track
   - Upload the AAB to Play Console -> Internal testing, install on test device and verify camera, scanning, generation, and update flow.

5) Staged rollout
   - When satisfied, upload to production with a staged rollout (5% -> 25% -> 100%).

6) Privacy & Data Safety
   - Ensure you have a Privacy Policy URL and completed Data Safety form (camera usage must be declared).

7) Optional: Notify users via FCM
   - If you configured Firebase and FCM, you can notify topic `/topics/releases` when the release is live.
   - Example quick script is `tools/send_fcm_notification.py` (legacy HTTP API). Prefer the HTTP v1 API with OAuth for production.

CI automation (example)
----------------------
- Add a GitHub Action that builds the AAB and (optionally) uploads to Play using `r0adkll/upload-google-play`.
- Provide `SERVICE_ACCOUNT_JSON` secret and `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` for signing.

If you'd like, I can add a sample GitHub Action workflow file to this repository and a signing helper script — tell me and I will create them.

