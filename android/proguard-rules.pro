# LedgerLens Android ProGuard Rules
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

# Keep SQLCipher
-keep class net.zetetic.** { *; }
-keepclassmembers class net.zetetic.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Kotlin datetime
-keep class kotlinx.datetime.** { *; }

# Android-specific rules
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Suppress warnings for missing classes that are okay to not have
-dontwarn org.slf4j.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
