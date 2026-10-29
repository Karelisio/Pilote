# Keep all app code (reflection: Shizuku user service, Room, serialization, Glance callbacks).
-keep class com.karelisio.pilote.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontwarn org.slf4j.**
