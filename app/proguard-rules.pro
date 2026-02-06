# GPT Auto-Reader ProGuard Rules

# Keep the Accessibility Service class
-keep class com.gpt.auto.reader.AutoReadService { *; }

# Keep Compose and Material components
-keep class androidx.compose.material3.** { *; }

# Standard Android safety
-keepattributes *Annotation*
-keepattributes Signature
-dontwarn com.google.errorprone.annotations.**