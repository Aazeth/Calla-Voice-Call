# Implementation Plan: Network Debug Logger

Menambahkan fitur **Network Debug Logger** untuk merekam, menampilkan secara real-time, menyalin, dan mengosongkan log event jaringan (WebSocket, WebRTC state, ICE Candidates, dan Connectivity changes) guna memudahkan analisis serta troubleshooting koneksi antar perangkat.

## User Review & Critical Decisions

- **Confirmed Choice (Capacity)**: Menyimpan maksimum 500 entri log terbaru di memori untuk mencegah memory leak.
- **Confirmed Choice (Viewer UI)**: Menggunakan Modal Bottom Sheet dengan tampilan font Monospace, opsi Auto-Scroll, serta aksi Copy & Clear.
- **Default State**: Logging dinonaktifkan secara default untuk efisiensi memori, dan disimpan pengaturannya secara terpusat di `SettingsStore`.

---

## 1. Overview & Core Concept

Fitur **Network Debug Logger** memberikan transparansi diagnostik jaringan secara langsung dari aplikasi Calla. Pengguna dapat mengaktifkan logging saat mengalami kendala koneksi WebRTC/WebSocket, lalu menyalin log lengkap atau memantau status secara real-time melalui viewer sheet tanpa bantuan komputer/ADB.

---

## 2. User Experience & Visual Design

### Settings Screen Integration
- Pada seksi **Network Relay** di Settings Sheet:
  - Tambahkan toggle switch **"Enable Network Logs"**.
  - Saat toggle diaktifkan (ON), tampilkan sub-panel aksi:
    - **"View Real-time Logs"** (Membuka Bottom Sheet viewer)
    - **"Copy Network Logs"** (Menyalin log ke clipboard dengan feedback Toast)
    - **"Clear Logs"** (Mengosongkan riwayat log aktif)

### Log Viewer Bottom Sheet
- Sheet terpisah dengan header judul **"Network Logs"**, indikator badge entri (misal `42 / 500`), serta tombol aksi (Auto-scroll toggle & Clear).
- Tampilan konten log menggunakan format `Monospace` (`FontFamily.Monospace`) dengan warna terstruktur:
  - Timestamp: `[HH:mm:ss]`
  - Tag/Category: `[WebSocket]`, `[WebRTC]`, `[ICE]`, `[Network]`
  - Pesan detail & indikator status (Misal: `Connected`, `Failed: timeout`).

---

## 3. Key Product Decisions & Trade-Offs

- **Memory Safety (Circular Buffer)**:
  - *Pendekatan*: Menggunakan buffer berukuran terbatas (maksimal 500 entri) yang dilindungi Thread-safe synchronization.
  - *Alasan*: Mencegah konsumsi memori tak terbatas jika koneksi mengalami reconnect berulang.
- **Reactive State Flow**:
  - *Pendekatan*: Menyediakan `StateFlow<List<LogEntry>>` di `NetworkLogger` yang hanya diperbarui jika toggle aktif.
  - *Alasan*: Komponen UI Compose dapat mendengarkan perubahan log secara real-time tanpa polling manual.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       Calla App UI                          │
│  SettingsSheet  ──►  NetworkLogViewerSheet (Monospace View) │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│       SettingsStore          │ │    NetworkLogger Singleton │
│ (SharedPreferences/Flow)     │ │ - Circular Buffer (500 max)│
│ - isLoggingEnabled: Boolean  │ │ - StateFlow<List<LogEntry>>│
└──────────────┬───────────────┘ └──────────────┬─────────────┘
               │                              ▲
               ▼                              │
┌─────────────────────────────────────────────┴───────────────┐
│              Network & WebRTC Layer                         │
│ - SignalingClient (WebSocket state & messages)              │
│ - WebRTCClient (IceConnectionState, IceGatheringState)      │
│ - ConnectivityObserver (Network Online/Offline transitions) │
└─────────────────────────────────────────────────────────────┘
```

### Plan Details:
1. **SettingsStore Integration**: Tambahkan `isLoggingEnabled` (StateFlow + SharedPreferences persistence).
2. **NetworkLogger Utility**: Singleton `NetworkLogger` dengan timestamp formatter `[HH:mm:ss]`, method `log(tag, message)`, `clear()`, dan `getFormattedLogs()`.
3. **Instrumentation**:
   - `SignalingClient.kt`: Catat event `Connecting`, `Connected`, `Closed`, `Failure`, serta payload signaling (Offer, Answer, ICE batch).
   - `WebRTCClient.kt`: Catat `onIceConnectionChange`, `onIceGatheringChange`, `onSignalingChange`, `onIceCandidate`.
   - `ConnectivityObserver.kt` / `CallViewModel`: Catat perubahan status koneksi data/Wi-Fi.
4. **UI Updates**: Update Settings screen dan tambahkan `NetworkLogViewerSheet`.
5. **App Version**: Update version name di `build.gradle.kts` menjadi `1.4.0` (versionCode 10).
