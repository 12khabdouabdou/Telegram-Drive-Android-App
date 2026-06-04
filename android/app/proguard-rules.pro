# Add project-specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# --- UniFFI-generated bindings --------------------------------------------
# UniFFI's Kotlin bindings use JNA + reflection. Keep generated classes and
# the JNA bridge so the Rust core can still be called after R8 minification.
-keep class uniffi.** { *; }
-keep class com.cameronamer.telegramdrive.generated.** { *; }
-keepclassmembers class uniffi.** { *; }

# JNA itself
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# --- Kotlin / Coroutines --------------------------------------------------
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Compose --------------------------------------------------------------
# Compose handles its own minification; these are belt-and-suspenders for
# reflection-based previews and tooling.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- Media3 / ExoPlayer ----------------------------------------------------
# ExoPlayer uses reflection to discover extractors and renderers.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- App entry points -----------------------------------------------------
# Keep services and the main activity; the manifest references them by name.
-keep class com.cameronamer.telegramdrive.MainActivity
-keep class com.cameronamer.telegramdrive.services.** { *; }
