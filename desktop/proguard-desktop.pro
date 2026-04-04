## Suppress platform-specific references not present on JVM desktop
-dontwarn okhttp3.internal.platform.**
-dontwarn okhttp3.internal.platform.android.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.**
-dontwarn com.twelvemonkeys.image.Magick**
-dontwarn magick.**

## Keep OkHttp/Kotlin metadata
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class kotlin.** { *; }

## Avoid warnings converting notes to errors
-dontnote **