# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.greenlight.app.**$$serializer { *; }

# Retrofit interfaces
-keep,allowobfuscation interface com.greenlight.app.data.tmdb.TmdbApi
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Firestore model classes read via reflection
-keep class com.greenlight.app.data.firestore.** { *; }
