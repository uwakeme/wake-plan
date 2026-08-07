# Wake Plan — ProGuard / R8 rules
# 单 module MVP，先不细化。release 阶段按需加。

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 应用 data class 序列化的 model 必须 keep
-keep,includedescriptorclasses class com.wakeplan.app.**$$serializer { *; }
-keepclassmembers class com.wakeplan.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.wakeplan.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
