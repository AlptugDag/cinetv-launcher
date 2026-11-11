# CineTV release: shrink, optimize and obfuscate everything that is not an Android entry point
# (activities, services and views referenced from the manifest are kept automatically).
-optimizationpasses 5
-repackageclasses 'c'
-allowaccessmodification
-overloadaggressively
-mergeinterfacesaggressively
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable

# Strip verbose and debug logs from release builds while keeping warnings and errors for diagnostics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# MediaTek's picture API and other vendor classes are reached by reflection and may be absent.
-dontwarn com.mediatek.**

# The phone remote's key bridge runs from the APK under app_process (see KeyInjector).
-keep class com.cinetv.launcher.remote.KeyInjector { public static void main(java.lang.String[]); }
# The cast sniffer page calls these from JavaScript by name
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

# NewPipeExtractor, Rhino JS engine, OkHttp, and Media3 ExoPlayer
# NewPipe: only the YouTube parts CineTV calls are kept (R8 follows the references);
# Rhino needs reflection for runtime classes, but never its Swing desktop debugger/shell tools.
-keep class !org.mozilla.javascript.tools.**,org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn androidx.media3.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
