# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.libretv.android.data.remote.dto.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json name *;
}

# ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
