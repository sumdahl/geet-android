# youtubedl-android unpacks Python and ffmpeg and parses yt-dlp's JSON with Jackson through reflection.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**
