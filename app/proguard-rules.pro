# Simpfun R8 / ProGuard rules
#
# 目标：只保留确实依赖反射、清单/XML、第三方 SDK 要求保留的类，
# 避免类似 `-keep class cn.jdnjk.simpfun.** { *; }` 这种会基本关闭混淆和收缩的宽规则。

# ---- Debuggability / reflection metadata ----
# Bugly 等崩溃上报需要行号；泛型/注解对部分 SDK 和回调解析有用。
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*
-renamesourcefileattribute SourceFile

# ---- Android framework entry points ----
# Manifest 中的 Activity/Receiver/Provider 通常会由 AGP 自动保留；这里补充 XML/Fragment
# 反射创建时最容易出问题的构造函数。
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public <init>(android.content.Context, android.util.AttributeSet, int, int);
}

-keepclasseswithmembers class * {
    native <methods>;
}

# WebView JS bridge：当前项目没有 addJavascriptInterface，但保留带注解的方法，方便后续扩展。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# androidx.annotation.Keep 标记的代码不混淆/不裁剪。
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# ---- Java language features ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---- Tencent Bugly ----
# 官方 SDK 依赖反射和上报符号信息，保守保留。
-keep class com.tencent.bugly.** { *; }
-dontwarn com.tencent.bugly.**

# ---- Alipay SDK ----
# 支付 SDK 内部有 Binder/反射/外部 App 兼容逻辑，保守保留。
-keep class com.alipay.** { *; }
-dontwarn com.alipay.**

# ---- SSH / crypto ----
# SSHJ 与 BouncyCastle 存在算法名、Provider、KEX/MAC/Cipher 等反射式查找，保守保留。
-keep class net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---- Sora editor / TextMate ----
# 编辑器和语法高亮组件类名较多，内部存在动态装载和服务发现，保守保留。
-keep class io.github.rosemoe.** { *; }
-dontwarn io.github.rosemoe.**

# ---- MPAndroidChart ----
# 图表 View 可能从 XML 创建，保留以避免资源引用类名被混淆后运行时找不到。
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ---- Glide ----
# 当前未声明自定义 AppGlideModule，依赖 Glide 自带 consumer rules 即可。
-dontwarn com.bumptech.glide.**

# ---- OkHttp / Okio ----
# 不 keep，允许 R8 收缩混淆；只忽略可选平台类告警。
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**

# ---- NanoHTTPD ----
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# ---- Desugaring / platform optional APIs ----
-dontwarn java.lang.invoke.StringConcatFactory
