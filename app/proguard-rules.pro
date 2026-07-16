# Project-specific ProGuard rules.

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.Annotations
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pockethub.**$$serializer { *; }
-keepclassmembers class com.pockethub.** {
    *** Companion;
}
-keepclasseswithmembers class com.pockethub.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**
