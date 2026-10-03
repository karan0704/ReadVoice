# kotlinx.serialization and OkHttp ship their own consumer rules.
# Keep generated serializers reachable for the DTOs in the data layer.
-keepclassmembers class com.readvoice.app.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
