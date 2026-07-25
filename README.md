# QR Genie v1.0.7

A modern, lightweight QR code scanner and generator for Android. Built with **Jetpack Compose** and **Material 3**, with a Royal Blue and Emerald Green brand identity.

## Features

* **Fast scanning** — ML Kit with a ZXing fallback for reliable detection, including non-Latin text (Urdu/Arabic).
* **Smart scan results** — Wi-Fi, contact, email, phone, and SMS QR codes surface one-tap actions (Connect, Add contact, Call, Email, Text) instead of raw text.
* **Custom QR generation** — plain text/URL, Wi-Fi network, and contact (vCard) QR codes.
* **11 languages** — English, Arabic, Urdu, Hindi, Bengali, Persian, Turkish, French, German, Spanish, Chinese, with full RTL support.
* **Dark mode** — follows system theme or can be set manually in Settings.
* **Scan/generate history** — with search and type filtering.
* **Local history only** — history is stored on-device; nothing is uploaded to a server.

## Design language

- **Royal Blue (`#2962FF`)**: scanning / utility actions.
- **Emerald Green (`#00C853`)**: generation / creation actions.
- Neutral card surfaces with brand color used as an accent (icon chips), not a full-bleed fill.

## Tech stack

- **Language:** Kotlin
- **UI framework:** Jetpack Compose (Material 3)
- **Camera:** CameraX
- **QR engine:** ML Kit + ZXing
- **Per-app language/theme:** AndroidX AppCompat (`AppCompatDelegate`)

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/EngrMahmood/QR-Genie.git
   ```
2. Open in Android Studio and run on a device/emulator (minSdk 26).
