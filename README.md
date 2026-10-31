# Calla

Calla adalah aplikasi Android native untuk panggilan suara 1-on-1 peer-to-peer berbasis WebRTC dengan kustomisasi bitrate audio Opus secara dinamis.

## Fitur Utama

- **WebRTC Peer-to-Peer Voice Call**: Koneksi suara langsung latensi rendah dengan dukungan Google STUN server (`stun.l.google.com:19302`).
- **Dynamic Opus Bitrate Control**: Modifikasi SDP untuk mengatur bitrate Opus mulai dari 64 kbps hingga 256 kbps (default 128 kbps) pada format 48 kHz fullband stereo.
- **6-Digit Room Signaling**: Pertukaran SDP Offer/Answer dan ICE Candidate secara instan melalui Cloud Firestore.
- **Foreground Audio Service**: Panggilan suara dan mikrofon tetap aktif saat aplikasi di-minimize atau layar mati dengan persistent notification.
- **Minimalist Material 3 UI**: Antarmuka bersih, modern, dan solid tanpa gradasi berlebihan.
- **Themed Adaptive Icon**: Ikon Calla Lily monokrom yang otomatis sinkron dengan warna Monet Android 13+.

## Prasyarat

- Android 7.0 (API Level 24) ke atas
- Koneksi Internet
- Google Play Services (untuk autentikasi Google Sign-In)

## Cara Build & Menjalankan

### Menggunakan Gradle CLI

1. Clone repositori:
   ```bash
   git clone <URL_REPOSITORI>
   cd calla
   ```

2. Build APK Debug:
   ```bash
   gradle :app:assembleDebug
   ```
   File APK tersimpan di:
   `app/build/outputs/apk/debug/app-debug.apk`

3. Build APK Release:
   ```bash
   gradle :app:assembleRelease
   ```
   File APK tersimpan di:
   `app/build/outputs/apk/release/app-release.apk`

4. Install ke perangkat:
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

### Menggunakan Android Studio

1. Buka folder proyek di Android Studio (Ladybug / Hedgehog atau yang lebih baru).
2. Biarkan Gradle melakukan sync dependensi.
3. Hubungkan perangkat fisik atau emulator Android.
4. Klik **Run** (`Shift + F10`).

## Arsitektur Singkat

- **UI**: Jetpack Compose (Material 3) + StateFlow
- **WebRTC**: `io.github.webrtc-sdk:android` (WebRTC Android Core)
- **Signaling**: Google Cloud Firestore
- **Audio Service**: Android Foreground Service (`foregroundServiceType="microphone"`)
- **Autentikasi**: Jetpack Credential Manager + Google Sign-In
