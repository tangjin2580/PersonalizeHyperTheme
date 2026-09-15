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
# libxposed 102 入口及 hook 助手：Api102Entry 只被 META-INF/xposed/java_init.list
# 这个「资源文件」间接引用，ProGuard shrink 会误判为无用类删掉，必须显式保留。
-keep class com.tangjin.personalizehyper.theme.Api102Entry { *; }
-keep class com.tangjin.personalizehyper.theme.Xp { *; }
-keep class com.tangjin.personalizehyper.theme.RearScreenMamlSkip { *; }
-keep class com.tangjin.personalizehyper.theme.RearScreenState { *; }
-keep class com.tangjin.personalizehyper.theme.LogHelper { *; }

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-dontobfuscate
-allowaccessmodification
-overloadaggressively