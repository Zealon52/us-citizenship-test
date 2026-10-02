# Add project specific ProGuard rules here.

# kotlinx.serialization needs the generated serializer() companion methods and the @Serializable
# model classes themselves kept, or JSON decoding silently breaks at runtime after shrinking.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.usctest.app.**$$serializer { *; }
-keepclassmembers class com.usctest.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.usctest.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.usctest.app.data.model.** { *; }

# Room generates implementation classes (*_Impl) reflectively resolved at runtime.
-keep class com.usctest.app.data.local.*_Impl { *; }
-keep class * extends androidx.room.RoomDatabase
