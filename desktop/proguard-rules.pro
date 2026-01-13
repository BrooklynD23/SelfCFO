# LedgerLens Desktop ProGuard Rules
# ===================================

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# Keep Kotlin Serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep LedgerLens models for serialization
-keep,includedescriptorclasses class com.ledgerlens.**$$serializer { *; }
-keepclassmembers class com.ledgerlens.** {
    *** Companion;
}
-keepclasseswithmembers class com.ledgerlens.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SQLDelight generated classes
-keep class com.ledgerlens.db.** { *; }
-keepclassmembers class com.ledgerlens.db.** { *; }

# Keep Compose Desktop classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Skiko (Compose Desktop rendering engine)
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin datetime
-keep class kotlinx.datetime.** { *; }

# Keep main entry point
-keep class com.ledgerlens.desktop.MainKt {
    public static void main(java.lang.String[]);
}

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JDBC and SQL classes for SQLite
-keep class java.sql.** { *; }
-keep class javax.sql.** { *; }
-keep class org.sqlite.** { *; }

# Suppress warnings for missing classes
-dontwarn org.slf4j.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
-dontwarn java.awt.**
