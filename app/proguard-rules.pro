# Hilt / Dagger generated components.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclasseswithmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }

# Matrix Rust SDK: UniFFI bindings are looked up by JNA through reflection and native symbol names.
-keep class org.matrix.rustcomponents.sdk.** { *; }
-keep class uniffi.** { *; }
# Reached from native code by JNI name: the context hand-off and the TLS verifier's Java half.
-keep class org.matrix.rustsdk.** { *; }
-keep class org.rustls.platformverifier.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.*

# kotlinx.serialization keeps its generated serializers via reflection on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.phalanx.android.** { *** Companion; }
-keepclasseswithmembers class org.phalanx.android.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class org.phalanx.android.**$$serializer { *; }

# Called by name from the native libraries (src/main/cpp/llm_jni.cpp, phalanx_stt.cpp): without
# these, R8 renames the callbacks and every reply or transcript ends in NoSuchMethodError.
-keep class org.phalanx.android.core.ai.LlamaNative { native <methods>; }
-keep interface org.phalanx.android.core.ai.TokenSink { *; }
-keep interface org.phalanx.android.core.ai.speech.SegmentSink { *; }
