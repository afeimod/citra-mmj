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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# JNI native methods
-keepclasseswithmembernames class * { native <methods>; }
-keep class org.citra.emu.NativeLibrary { *; }
-keep interface org.citra.emu.NativeLibrary$OnScreenshotCompleteListener {*;}

# 全局禁用 R8 优化（保留裁剪/混淆），规避 R8 full mode 下生成的非法字节码。
# 配合 gradle.properties 里 android.enableR8.fullMode=false 一起使用，参考 afeimod/NesStation 项目。
-dontoptimize

# R8 报告的缺失类（来自 androidx/cryptopp/tink 等依赖的注解，运行时用不到）：
# "Missing class com.google.errorprone.annotations.Immutable (referenced from: com.google.crypto.tink.KeyTemplate)"
# 这些是 compileOnly 注解（仅编译期检查用），运行时不存在；用 -dontwarn 抑制。
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.lang.model.**
-dontwarn org.checkerframework.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn com.android.tools.r8.**
-dontwarn kotlin.annotations.**

# 保持 cubeb / 音频相关 native 调用
-keep class org.citra.emu.audio.** { *; }

# 保持 Service/Activity 类（manifest 反射引用）
-keep class org.citra.emu.ui.** { *; }
-keep class org.citra.emu.settings.** { *; }
-keep class org.citra.emu.overlay.** { *; }
