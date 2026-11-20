# ProGuard rules for WhatsApp Logger Native Android App

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Keep Room entities and DAOs
-keep class com.whatsapp.logger.data.local.** { *; }

# Keep Retrofit models
-keep class com.whatsapp.logger.data.api.** { *; }

# Keep ViewModels
-keep class com.whatsapp.logger.viewmodel.** { *; }

# Keep Compose UI
-keep class com.whatsapp.logger.ui.** { *; }

# Keep Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep OkHttp EventSource
-keep class okhttp3.sse.** { *; }

# Keep Biometric
-keep class androidx.biometric.** { *; }

# Keep Security Crypto
-keep class androidx.security.crypto.** { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep Kotlinx Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Kotlinx Datetime
-keep class kotlinx.datetime.** { *; }

# Don't warn about missing classes
-dontwarn okhttp3.**
-dontwarn kotlinx.**
-dontwarn androidx.**
-dontwarn com.google.dagger.**
-dontwarn dagger.**

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}