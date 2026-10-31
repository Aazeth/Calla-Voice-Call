# Implementation Plan: Enlarge Calla Header Icon

Memperbesar ukuran visual icon Calla di tengah lingkaran background header pada `LobbyScreen`.

## Proposed Changes

### Header Logo Scaling
#### [app/src/main/java/com/aazeth/calla/ui/CallaApp.kt]
- Pada `LobbyScreen` TopAppBar header logo:
  - Terapkan `scale(1.35f)` atau sesuaikan `Modifier.size(...)` pada `Image` logo Calla (`R.drawable.ic_launcher_foreground`).
  - Tambahkan `Modifier.clip(CircleShape)` pada `Box` container agar gambar tetap terpotong rapi di dalam lingkaran background.
  - Ini akan membuat elemen bunga Calla di dalam lingkaran background tampil lebih besar, jelas, dan konsisten dengan icon launcher aplikasi.

---

## Verification Plan

### Automated Tests
- Jalankan `compile_applet` untuk memastikan kompilasi Compose berjalan sukses tanpa error.
