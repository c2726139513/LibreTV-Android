# Moshi 生成的 JsonAdapter 需要保留 DTO 类
-keep class com.vidhub.android.data.remote.dto.** { *; }
-keep class com.vidhub.android.model.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Retrofit
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# 保留泛型签名（Retrofit/Moshi 反射依赖）
-keepattributes Signature
# 保留带 Retrofit 注解的服务接口方法（VidHubApi）
-keep,allowobfuscation,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}

# Kotlin Metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ExoPlayer
-dontwarn androidx.media3.**
