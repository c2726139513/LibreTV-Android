# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.libretv.android.data.remote.** { *; }

# Moshi: keep generated @JsonClass adapters
-keep class **._JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
# KotlinJsonAdapterFactory needs @kotlin.Metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
