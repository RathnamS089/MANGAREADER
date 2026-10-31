# Source definitions are decoded with kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class dev.inkdot.**$$serializer { *; }
-keepclassmembers class dev.inkdot.** { *** Companion; }
-keepclasseswithmembers class dev.inkdot.** { kotlinx.serialization.KSerializer serializer(...); }

# Optional jsoup / okhttp dependencies
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
