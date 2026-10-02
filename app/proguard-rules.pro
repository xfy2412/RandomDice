# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── 为什么这里没有枚举的 keep 规则（是量过的，不是忘了）─────────────────────
# settings.txt 把枚举常量名直接当磁盘格式用（"Plastic" / "BestOf" / "FirstDie"…），
# 所以"R8 会不会改枚举常量名"必须回答：改了的话，老版本写下的设置文件会读不出来，
# 静默回落到默认值（不报错，最难查的那种）。
#
# 实测（AGP 8.12.3 / R8 full mode，不开任何 keep 规则）：编译产物 classes.dex 里
# 这些字面量全部存活，各一份 —— 而源码里没有硬编码它们，所以那一份只可能是枚举
# 构造时传给 name 的字符串 ⇒ 运行时 .name 不变，跨版本读写设置文件都成立。
#
# 复查方法：解开 apk，在 classes.dex 里搜 "Plastic"/"BestOf"/"FirstDie" 等字面量。
# 升级 AGP/R8 之后这条要重验；症状是设置项（规则/音色/摇一摇开关）莫名回到默认。

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