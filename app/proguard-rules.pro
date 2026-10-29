# Project specific ProGuard rules for xnode

# Preserve model classes
-keep class com.example.model.** { *; }

# Preserve Activities and Services
-keep class com.example.OverlayService { *; }
-keep class com.example.SettingsActivity { *; }
-keep class com.example.MainActivity { *; }

# Preserve Kotlin reflection/coroutines metadata if present
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep R class fields to avoid issues with dynamic resource lookups
-keepclassmembers class **.R$* {
    public static <fields>;
}
