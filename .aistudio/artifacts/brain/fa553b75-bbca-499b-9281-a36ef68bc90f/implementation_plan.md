# Implementation Plan - In-Call Ping Indicator

Add a real-time WebRTC ping latency indicator at the top-center of the Call Screen with green/yellow/red connection quality status updated every 2 seconds.

## User Preferences
- **Style**: Mini pill badge with dynamic quality color indicator (Green < 100ms, Yellow 100-250ms, Red > 250ms).
- **Refresh Rate**: Polled every 2 seconds during active WebRTC calls.

## Proposed Changes

### 1. WebRTC & ViewModel Layer
- **`WebRTCClient.kt`**: Add `getStats()` query function to compute round-trip time (RTT) from active ICE candidate pairs.
- **`CallViewModel.kt`**: Add a 2-second periodic coroutine job when call status is `CONNECTED` to fetch RTT and update `CallUiState.pingMs`.
- **`CallUiState`**: Add field `val pingMs: Int? = null`.

### 2. UI Layer
- **`CallScreen.kt`**:
  - Add top-center floating badge component containing a status dot and formatted ping value (e.g. `42 ms`).
  - Color coding:
    - Green (`#4CAF50`): Ping < 100 ms
    - Yellow (`#FFC107`): Ping 100 ms - 249 ms
    - Red (`#F44336`): Ping >= 250 ms
  - Position below top inset padding to avoid overlapping system bars or notch.

## Verification Plan

### Automated Tests
- Run `gradle :app:testDebugUnitTest` to verify unit test suite.
- Run `compile_applet` to verify compilation.
