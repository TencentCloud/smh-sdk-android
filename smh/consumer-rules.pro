# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


-keep class com.tencent.cloud.smh.api**
-keep class com.tencent.cloud.smh.api.** {*;}

-keep class com.tencent.cloud.smh.ext**
-keep class com.tencent.cloud.smh.ext.** {*;}

-keep class com.tencent.cloud.smh.track**
-keep class com.tencent.cloud.smh.track.** {*;}

-keep class com.tencent.cloud.smh.transfer**
-keep class com.tencent.cloud.smh.transfer.** {*;}



-keep class com.tencent.cloud.smh.SMHCollection
-keep class com.tencent.cloud.smh.SMHCollection {*;}
-keep class com.tencent.cloud.smh.SMHCollectionFuture
-keep class com.tencent.cloud.smh.SMHCollectionFuture {*;}

-keep public class * extends com.tencent.cloud.smh.SMHException
-keep public class * extends com.tencent.cloud.smh.SMHClientException