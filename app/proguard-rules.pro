# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Kotlin Serialization ---
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.json.JsonObject { *; }
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers class ** {
    *** Companion;
    *** $serializer;
}

# --- Ktor & OkHttp ---
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn io.ktor.**
-dontwarn okhttp3.**

# --- Koin ---
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Data Models (Keep them to prevent obfuscation breaking JSON) ---
-keep class com.bluemix.clients_lead.data.repository.** { *; }
-keep class com.bluemix.clients_lead.domain.repository.** { *; }
-keep class com.bluemix.clients_lead.domain.model.** { *; }

# --- Ktor Content Negotiation (prevents release-build crashes) ---
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.client.plugins.contentnegotiation.** { *; }
-dontwarn io.ktor.serialization.**

# --- Kotlinx Serialization (keep generated serializers) ---
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# --- Google Maps & Places SDK ---
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.gms.**

# --- Coil (image loading) ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
