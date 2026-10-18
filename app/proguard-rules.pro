# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.trendscout.**$$serializer { *; }
-keepclassmembers class com.trendscout.** { *** Companion; }
-keepclasseswithmembers class com.trendscout.** { kotlinx.serialization.KSerializer serializer(...); }
