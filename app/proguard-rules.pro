# InstaGRUM is a local simulator; no networking rules are required.

# kotlinx.serialization writes and reads every saved profile. R8 must not rename
# or strip these, or an optimized release build would fail to load existing data.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations
-dontnote kotlinx.serialization.**

-keepclassmembers class com.instagrum.local.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.instagrum.local.model.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.instagrum.local.model.** { *; }

-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates the background worker reflectively by class name.
-keep class com.instagrum.local.notifications.ActivityWorker { <init>(...); }
