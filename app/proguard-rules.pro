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
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.* <fields>;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }

-keep class org.json.** { *; }

# Keep all classes in our base package and its subpackages
-keep class cn.jdnjk.simpfun.** { *; }

-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class cn.jdnjk.simpfun.model.* {
    <fields>;
    <init>(...);
    <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes *Annotation*

-keepclassmembers class **.R$* {
    public static <fields>;
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep AndroidX classes
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep support library classes
-keep class android.support.** { *; }
-dontwarn android.support.**

# Keep Gson classes
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep Bugly classes
-keep public class com.tencent.bugly.**{*;}
-dontwarn com.tencent.bugly.**

-keep class com.alipay.**{*;}
-dontwarn com.alipay.**

-keep class io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.sora.**