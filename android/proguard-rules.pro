# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Compose classes
-keep class androidx.compose.** { *; }

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
