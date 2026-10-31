# Implementation Plan: Xirsys TURN Integration & Auto ICE Restart Retry

Menyelesaikan masalah kegagalan koneksi WebRTC antar jaringan ("Connecting" -> "FAILED") yang disebabkan oleh Symmetric NAT pada ISP seluler/Wi-Fi. Mengintegrasikan **Xirsys TURN Service** yang stabil untuk menghasilkan kandidat `relay` (TURN) yang valid, serta menambahkan mekanisme **Auto ICE Restart Retry** hingga 3 kali saat terjadi timeout atau disconnect.

## User Review & Critical Decisions

- **Confirmed Choice (TURN Provider)**: Mengintegrasikan Xirsys STUN/TURN global endpoints yang menjamin pemintasan Symmetric NAT / Carrier-Grade NAT (CGNAT).
- **Confirmed Choice (Auto Reconnect)**: Otomatis melakukan pencobaan ulang ICE Restart hingga 3 kali saat status `FAILED` / `DISCONNECTED` terdeteksi sebelum menyerah dan menampilkan pesan error.

---

## 1. Overview & Core Concept

Berdasarkan analisis log jaringan, Host (`36.74.218.118`) dan Guest (`125.166.0.208`) hanya dapat menghasilkan kandidat `host` (LAN) dan `srflx` (STUN). Karena kedua penyedia internet berada di balik **Symmetric NAT**, koneksi langsung P2P tidak dapat dibentuk tanpa relay. Dengan menambahkan server **Xirsys TURN** (UDP + TCP di port 80/443), trafik suara di-relay dengan lancar. Jika terdapat kendala fluktuasi sinyal, mekanisme Auto ICE Restart Retry akan mencoba menyambungkan kembali hingga 3x secara otomatis.

---

## 2. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                        Calla App UI                         │
│ - Status: "Connecting..." ──► "Reconnecting (Attempt 1/3)"  │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│         CallViewModel        │ │    NetworkLogger Singleton │
│ - iceRestartRetryCount: 0..3 │ │ - Catat event STUN/TURN    │
│ - autoIceRestartRetry()      │ │ - Catat status reconnect   │
└──────────────┬───────────────┘ └──────────────┬─────────────┘
               │                              │
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        WebRTCClient                         │
│ - STUN: global.stun.xirsys.com                              │
│ - TURN: global.turn.xirsys.com (UDP/TCP port 80, 443, 3478) │
│ - Automatic ICE Restart trigger                             │
└─────────────────────────────────────────────────────────────┘
```

### Plan Details:
1. **Xirsys TURN Configuration**:
   - Di `WebRTCClient.kt`: Tambahkan daftar endpoint STUN/TURN Xirsys global yang beroperasi di UDP & TCP (port 80, 443, 3478) dengan kredensial terautentikasi.
2. **Auto ICE Restart Retry Logic**:
   - Di `CallViewModel.kt`:
     - Tambahkan variabel `iceRestartRetryCount` (maksimal 3x).
     - Saat `handleConnectionStatus` menerima status `FAILED` atau `DISCONNECTED` saat dalam panggilan:
       - Jika `iceRestartRetryCount < 3`: Tingkatkan counter, ubah teks status UI menjadi `"Reconnecting WebRTC (Attempt X/3)..."`, lalu panggil `webRtcClient?.restartIce(...)` dan kirim offer restart ICE baru via signaling.
       - Jika `iceRestartRetryCount >= 3`: Ubah status menjadi `FAILED` dan tampilkan error banner.
     - Reset `iceRestartRetryCount = 0` begitu status berubah ke `CONNECTED`.
3. **App Version**: Update `versionCode = 11` dan `versionName = "1.4.1"` di `build.gradle.kts`.
