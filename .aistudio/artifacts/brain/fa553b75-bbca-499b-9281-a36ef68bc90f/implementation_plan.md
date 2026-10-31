# Implementation Plan: Calla UI Revisions & Modernization

Memperbarui beberapa elemen visual pada aplikasi Calla sesuai dengan instruksi revisi terbaru.

## Proposed Changes

### 1. Header Icon Replacement
#### [app/src/main/java/com/aazeth/calla/ui/CallaApp.kt]
- Ganti icon `Icons.Default.GraphicEq` pada TopAppBar header di `LobbyScreen` dengan icon resmi bunga Calla (`R.drawable.ic_launcher_foreground`).
- Sesuaikan ukuran dan perpaduan warna (tint/background) agar tampil presisi dan menarik.

### 2. Status Bar Header Spacing Reduction
#### [app/src/main/java/com/aazeth/calla/ui/CallaApp.kt]
- Sesuaikan `windowInsets` pada `TopAppBar` `LobbyScreen` agar jarak dari status bar bagian atas berkurang 50% (`statusBarsTopPadding / 2`).

### 3. Settings Layout Refinement (Minimalist & Modern)
#### [app/src/main/java/com/aazeth/calla/ui/CallaApp.kt]
- Perbarui desain `AudioDspSettingsDialog`:
  - Desain ulang Tab Navigation (Audio DSP & TURN Relay) dengan pill-shaped active indicator.
  - Rapikan kartu status mode (Raw Audio vs Filtered Audio) dan kartu TURN status dengan border halus dan radius sudut modern (16.dp).
  - Terapkan tata letak minimalis pada toggle switch dan text field input server custom.
  - Perbaiki spacing (8.dp - 16.dp grid) dan hierarki font agar lebih ringan, elegan, dan estetik.

---

## Verification Plan

### Automated Tests
- Eksekusi unit test `gradle :app:testDebugUnitTest` untuk memastikan tidak ada fungsi yang terganggu.
- Jalankan `compile_applet` untuk memverifikasi tidak ada error sintaks atau kompilasi Compose.
