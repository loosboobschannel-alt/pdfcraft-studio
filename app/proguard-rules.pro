# PDFBox Android — optional JPEG2000 (Gemalto) not on Android classpath
-dontwarn com.gemalto.jp2.**
-dontwarn com.gemalto.**

# Other optional PDFBox / font deps
-dontwarn org.apache.commons.**
-dontwarn org.bouncycastle.**

# Keep app + Compose
-keep class com.pdfcraft.studio.** { *; }
-keep class androidx.compose.** { *; }

# Enums / Parcelable safety
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
