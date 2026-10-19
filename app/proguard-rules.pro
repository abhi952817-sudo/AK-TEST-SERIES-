# Strip logging calls in release builds to prevent leaking sensitive data in logcat
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep kotlinx.serialization models if used
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class com.ak.testseries.** { *; }

