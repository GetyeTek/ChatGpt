# GPT Auto-Reader ProGuard Rules

# Keep the Accessibility Service class
-keep class com.gpt.auto.reader.AutoReadService { *; }

# Keep Compose and Material components
-keep class androidx.compose.material3.** { *; }

# Standard Android safety
-keepattributes *Annotation*
-keepattributes Signature
-dontwarn com.google.errorprone.annotations.**

# Aggressive stripping for Compose
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep our logic intact
-keep class com.gpt.auto.reader.** { *; }