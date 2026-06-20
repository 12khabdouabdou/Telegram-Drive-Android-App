# TDLib / Telegram-related ProGuard rules

# TDLib (org.drinkless.tdlib) — keep all JNI entry points
-keep class org.drinkless.tdlib.** { *; }
-keep class org.drinkless.tdlib.Client { *; }
-keep class org.drinkless.tdlib.TdApi$* { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.telegramdrive.app.**$$serializer { *; }
-keepclassmembers class com.telegramdrive.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.telegramdrive.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# WorkManager
-keep class androidx.work.impl.** { *; }

# Compose
-keep class androidx.compose.** { *; }
