# Add project specific ProGuard rules here.
# WebRTC rules to prevent shrinking native interfaces
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp & Okio rules
-dontwarn okhttp3.**
-dontwarn okio.**
