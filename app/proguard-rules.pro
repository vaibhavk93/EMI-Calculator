# kotlinx.serialization keeps its generated serializers via @Serializable metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.vaibhav.emicalc.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.vaibhav.emicalc.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generates implementations that are referenced reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
