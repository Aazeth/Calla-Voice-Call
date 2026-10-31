# WebRTC rules to prevent shrinking native interfaces
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# OkHttp & Okio rules
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Calla App model and ViewModel
-keep class com.aazeth.calla.** { *; }
-keepclassmembers class com.aazeth.calla.** { *; }
