-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

-keep class androidx.core.content.FileProvider { *; }
-keep class com.example.workplacegallery.** { *; }
