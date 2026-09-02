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

# Keep SnakeYAML (GitHub issue form templates) - it reflects during YAML load
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# Hilt @HiltWorker entry points (consumer rules cover factories; belt & suspenders)
-keepclasseswithmembers class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# Readable crash traces after obfuscation (retrace-able, ~tens of KB)
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn kotlinx.coroutines.debug.**
-dontwarn java.lang.invoke.StringConcatFactory
