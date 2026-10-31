# Implementation Plan: Calla Settings Redesign to Modal Bottom Sheet

Refactoring dan redesain total komponen Pengaturan (Settings) dari `AlertDialog` melayang menjadi `ModalBottomSheet` Material 3 yang modern, minimalis, dan terstruktur.

## Proposed Changes

### Build Configuration
#### [app/build.gradle.kts]
- Update `versionCode = 7`
- Update `versionName = "1.2.0"`

---

### Settings Screen Redesign
#### [app/src/main/java/com/aazeth/calla/ui/CallaApp.kt]
- Ganti `AudioDspSettingsDialog` dengan `SettingsBottomSheet` menggunakan `ModalBottomSheet` `@OptIn(ExperimentalMaterial3Api::class)`.
- **Header:**
  - Judul "Settings" di sebelah kiri (`titleLarge` / `bold`).
  - Tombol icon silang `Icons.Default.Close` di kanan atas untuk menutup sheet.
- **Section Audio DSP (Bagian Atas):**
  - Hapus layout dua tab dan banner.
  - Gunakan `SingleChoiceSegmentedButtonRow` / `SegmentedButton` dengan 3 opsi:
    - `"Raw Audio"` (Semua filter DSP diatur `false`).
    - `"Balanced"` (Semua filter DSP diatur `true`).
    - `"Custom"` (Menampilkan Card berisi 4 switch `ListItem` M3: Noise Suppression, Echo Cancellation, Auto Gain Control, High-pass Filter).
- **Section Network Relay (Bagian Bawah):**
  - Beri pembatas `HorizontalDivider` dan header seksi `"Network Relay"`.
  - Satu toggle switch utama: `"Use Custom TURN Server"` (default `OFF` menggunakan OpenRelay bawaan).
  - Ketika switch diaktifkan (`ON`), kolom input `OutlinedTextField` untuk URL, Username, dan Password akan mekar secara dinamis.
- **Footer:**
  - Teks kecil terpusat di paling bawah: `"Calla v1.2.0"` dengan warna `onSurfaceVariant` / muted text.

---

## Verification Plan

### Automated Tests
- Jalankan `gradle :app:testDebugUnitTest` untuk memverifikasi unit test.
- Eksekusi `compile_applet` untuk memastikan kompilasi Compose dan `ModalBottomSheet` sukses tanpa error.
