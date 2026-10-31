# Implementation Plan: Voice Call Rooms Card Consolidation

Menggabungkan seksi pembuatan ruangan ("Host a New Room") dan masuk ruangan ("Join Existing Room") menjadi satu Card terpadu bertajuk "Voice Call Rooms" menggunakan `SingleChoiceSegmentedButtonRow`.

## Proposed Changes

### Build Configuration
#### [app/build.gradle.kts]
- Update `versionCode = 8`
- Update `versionName = "1.3.0"`

---

### UI Consolidation in Lobby Screen
#### [app/src/main/java/com/aazeth/calla/ui/CallaApp.kt]
- Pada `LobbyScreen`:
  - Buat satu `Card` tunggal bertajuk **"Voice Call Rooms"**.
  - Tambahkan `SingleChoiceSegmentedButtonRow` dengan 2 opsi:
    - `"Create Room"` (Index 0, default)
    - `"Join Room"` (Index 1)
  - **Opsi "Create Room":**
    - Tampilkan teks deskripsi singkat tentang pembentukan ruangan baru.
    - Tampilkan tombol utama **"Create Room"**.
  - **Opsi "Join Room":**
    - Tampilkan field input `"6-Digit Code"` (`OutlinedTextField`) secara dinamis saat mode `"Join Room"` dipilih.
    - Tampilkan tombol **"Join Room"** di bawahnya.

---

## Verification Plan

### Automated Tests
- Jalankan `gradle :app:testDebugUnitTest` untuk memverifikasi fungsionalitas unit test.
- Jalankan `compile_applet` untuk memverifikasi kelancaran kompilasi Compose.
