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
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Retrofit, Gson
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes AnnotationDefault

# 为了防止反射报错，保留数据模型类
-keep class com.zlearn.data.remote.** { *; }
-keepclassmembers class com.zlearn.data.remote.** { *; }

# Keep DTOs used by Gson/Retrofit
-keep class com.zlearn.data.remote.AuthRequest { *; }
-keep class com.zlearn.data.remote.AuthResponse { *; }
-keep class com.zlearn.data.remote.SyncQuestionDto { *; }
-keep class com.zlearn.data.remote.SyncQuestionsRequest { *; }
-keep class com.zlearn.data.remote.SyncQuestionsResponse { *; }
-keep class com.zlearn.data.remote.QuestionDetailResponse { *; }

# Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class com.zlearn.**_HiltComponents* { *; }

# OKHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# 如果使用了 Kotlin 序列化
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
