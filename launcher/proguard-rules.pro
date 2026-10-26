# CineTV release: shrink, optimize and obfuscate everything that is not an Android entry point
# (activities, services and views referenced from the manifest are kept automatically).
-repackageclasses 'c'
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable

# MediaTek's picture API and other vendor classes are reached by reflection and may be absent.
-dontwarn com.mediatek.**

# The phone remote's key bridge runs from the APK under app_process (see KeyInjector).
-keep class com.cinetv.launcher.remote.KeyInjector { public static void main(java.lang.String[]); }
# The cast sniffer page calls these from JavaScript by name
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
