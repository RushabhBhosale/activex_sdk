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
-keep class com.qingniu.scale.model.BleScaleData{*;}


#ppbase
-keep class com.lefu.ppbase.** {*;}
#ppbluetoothkit
-keep class com.peng.ppscale.vo.** {*;}
-keep class com.peng.ppscale.util.** {*;}
-keep class com.peng.ppscale.business.torre.vo.** {*;}
#ppcalculate
-keep class com.lefu.ppcalculate.data.** {*;}
-keep class com.besthealth.bhBodyComposition.** {*;}
-keep class com.besthealth.bh1BodyComposition.** {*;}
-keep class com.besthealth.bh2BodyComposition.** {*;}
-keep class com.besthealth.bh3BodyComposition.** {*;}
-keep class com.besthealth.bh4BodyComposition.** {*;}
-keep class com.besthealth.bh5BodyComposition.** {*;}
#Built in gson
-keep class * extends com.lefu.gson.reflect.TypeToken
-keep class com.lefu.gson.** {*;}
#bluetooth lib
-keep class com.lefu.bluetooth.library.** {*;}